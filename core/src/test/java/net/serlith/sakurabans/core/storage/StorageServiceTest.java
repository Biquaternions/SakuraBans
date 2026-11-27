package net.serlith.sakurabans.core.storage;

import java.net.InetAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.CompletionException;
import net.serlith.sakurabans.core.TestConfig;
import net.serlith.sakurabans.core.configuration.ConfigException;
import net.serlith.sakurabans.core.storage.dbo.SakuraTables;
import net.serlith.sakurabans.core.storage.repository.MetaRepository;
import net.serlith.sakurabans.core.util.ReleaseChannel;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

class StorageServiceTest extends DatabaseTest {

    @Test
    void runsQueriesOnWorkers() {
        if (this.onH2()) {
            Assertions.assertThat(this.dir.resolve("sakurabans.mv.db")).isRegularFile();
        }
        final String thread = this.storage.read(dsl -> Thread.currentThread().getName()).join();
        Assertions.assertThat(thread).startsWith("sakurabans-worker-");
        Assertions.assertThat(this.storage.read(dsl -> dsl.fetchCount(SakuraTables.SCOPES)).join())
                .isOne();
    }

    @Test
    void failedWritesRollBack() {
        Assertions.assertThatThrownBy(() -> this.storage.write(configuration -> {
            configuration.dsl().insertInto(SakuraTables.META)
                    .set(SakuraTables.META.K, "x").set(SakuraTables.META.V, "1").execute();
            throw new IllegalStateException("boom");
        }).join()).isInstanceOf(CompletionException.class);

        Assertions.assertThat(new MetaRepository(this.storage).get("x").join()).isEmpty();
    }

    @Test
    void stoppedStorageFailsFutures() {
        this.storage.stop();

        Assertions.assertThat(this.storage.running()).isFalse();
        Assertions.assertThatThrownBy(() -> this.storage.read(dsl -> 1).join())
                .hasCauseInstanceOf(IllegalStateException.class);
    }

    /** Another server's start against this test's database, with its own storage.yml. */
    private StorageService another(final String storageYaml, final AddressHasher hasher)
            throws Exception {
        final Path files = this.dir.resolve("another");
        Files.createDirectories(files);
        final String connection = !this.onH2()
                ? Files.readString(this.dir.resolve("storage.yml"))
                        .replaceAll("(?m)^address-hash-key:.*$", "")
                : "h2:\n  file: '" + this.dir.resolve("sakurabans") + "'\n";
        Files.writeString(files.resolve("storage.yml"), connection + storageYaml);
        // The H2 file is opened by its absolute path, the folder only holds the files.
        return new StorageService(files, TestConfig.load(files, "", this.log),
                new SchemaManager(this.log.logger(), ReleaseChannel.current()), hasher,
                this.executors, this.log.logger());
    }

    @Test
    void theSameKeyStartsAndHashesAlikeOnSecondServers() throws Exception {
        final String key = this.config.storage().addressHashKey();
        final AddressHasher hasher = new AddressHasher();
        final StorageService second = this.another("address-hash-key: '" + key + "'\n", hasher);

        second.start();
        try {
            final InetAddress address = InetAddress.getByName("203.0.113.7");
            Assertions.assertThat(hasher.hash(address)).isEqualTo(this.hasher.hash(address));
        } finally {
            second.stop();
        }
        Assertions.assertThat(new MetaRepository(this.storage)
                .get(StorageService.ADDRESS_HASH_KEY_CHECK).join())
                .contains(this.hasher.fingerprint()).get().asString().doesNotContain(key);
    }

    @Test
    void anotherKeyOnTheSameDatabaseRefusesToStartAndSaysWhatToCopy() throws Exception {
        final AddressHasher hasher = new AddressHasher();
        final StorageService second = this.another(
                "address-hash-key: 'a-different-key-of-a-new-server-0123456789'\n", hasher);

        Assertions.assertThatThrownBy(second::start).isInstanceOf(StorageException.class)
                .hasMessageContaining("address-hash-key is not the key this database was set"
                        + " up with")
                .hasMessageContaining("copy the address-hash-key line from the storage.yml"
                        + " of a server that already uses this database")
                .hasMessageNotContaining("a-different-key");
        Assertions.assertThat(second.running()).isFalse();
        Assertions.assertThat(hasher.ready()).isFalse();
        Assertions.assertThat(new MetaRepository(this.storage)
                .get(StorageService.ADDRESS_HASH_KEY_CHECK).join())
                .as("left as it was").contains(this.hasher.fingerprint());
    }

    @Test
    void missingKeysRefuseToStart() throws Exception {
        Assertions.assertThatThrownBy(() -> this.another("", new AddressHasher()))
                .hasRootCauseInstanceOf(ConfigException.class)
                .rootCause().hasMessageContaining("address-hash-key: must be set");
    }

    @Test
    void metaValuesCanBeSetReplacedAndRemoved() {
        final MetaRepository meta = new MetaRepository(this.storage);

        meta.put("display_id_secret", "a").join();
        Assertions.assertThat(meta.putIfAbsent("display_id_secret", "b").join()).isFalse();
        meta.put("display_id_secret", "c").join();
        Assertions.assertThat(meta.get("display_id_secret").join()).contains("c");
        meta.remove("display_id_secret").join();
        Assertions.assertThat(meta.get("display_id_secret").join()).isEmpty();
    }

    @Test
    void oneInstanceHoldsLeasesUntilTheyExpireOrAreReleased() throws InterruptedException {
        final Lease first = new Lease(this.storage, UUID.randomUUID());
        final Lease second = new Lease(this.storage, UUID.randomUUID());
        final Duration ttl = Duration.ofSeconds(30);

        Assertions.assertThat(first.tryAcquire("sweeper", ttl).join()).isTrue();
        Assertions.assertThat(second.tryAcquire("sweeper", ttl).join()).isFalse();
        Assertions.assertThat(first.tryAcquire("sweeper", ttl).join()).as("renewal").isTrue();

        first.release("sweeper").join();
        Assertions.assertThat(second.tryAcquire("sweeper", Duration.ofMillis(1)).join()).isTrue();
        // Expiry is the database's time, so the test waits for it.
        Thread.sleep(50);
        Assertions.assertThat(first.tryAcquire("sweeper", ttl).join()).as("expired").isTrue();
        Assertions.assertThat(second.tryAcquire("sweeper", ttl).join()).isFalse();

        second.release("sweeper").join();
        Assertions.assertThat(second.tryAcquire("sweeper", ttl).join())
                .as("not second's to release").isFalse();
        first.release("sweeper").join();
        Assertions.assertThat(second.tryAcquire("sweeper", ttl).join()).isTrue();
    }

    @Test
    void hostClocksRunningAheadDoNotTakeAnotherServersLease() {
        final Lease first = new Lease(this.storage, UUID.randomUUID());
        final Lease second = new Lease(this.storage, UUID.randomUUID());
        final Duration ttl = Duration.ofSeconds(30);
        Assertions.assertThat(first.tryAcquire("sweeper", ttl).join()).isTrue();

        // The hosts' clocks (the tests' clock) are not what a lease goes by.
        this.now.set(this.now.get().plus(Duration.ofMinutes(10)));
        Assertions.assertThat(second.tryAcquire("sweeper", ttl).join()).isFalse();
        this.now.set(this.now.get().minus(Duration.ofMinutes(20)));
        Assertions.assertThat(first.tryAcquire("sweeper", ttl).join()).as("renewal").isTrue();
        Assertions.assertThat(second.tryAcquire("sweeper", ttl).join()).isFalse();
    }
}
