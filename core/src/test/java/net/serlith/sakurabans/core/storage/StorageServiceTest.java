package net.serlith.sakurabans.core.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.time.InstantSource;
import java.util.UUID;
import java.util.concurrent.CompletionException;
import java.util.concurrent.atomic.AtomicReference;
import net.serlith.sakurabans.core.RecordingLogger;
import net.serlith.sakurabans.core.concurrent.SakuraExecutors;
import net.serlith.sakurabans.core.configuration.ConfigService;
import net.serlith.sakurabans.core.storage.dbo.SakuraTables;
import net.serlith.sakurabans.core.storage.repository.MetaRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class StorageServiceTest {

    @TempDir
    private Path dir;

    private final RecordingLogger log = new RecordingLogger();
    private final SakuraExecutors executors = new SakuraExecutors(failure -> { });
    private final AtomicReference<Instant> now = new AtomicReference<>(Instant.ofEpochSecond(100));
    private final InstantSource clock = () -> this.now.get();
    private StorageService storage;

    @BeforeEach
    void start() throws Exception {
        final ConfigService config = new ConfigService(this.dir, this.log.logger());
        config.load();
        this.storage = new StorageService(this.dir, config, new SchemaManager(this.log.logger()),
                this.executors, this.log.logger());
        this.storage.start();
    }

    @AfterEach
    void stop() {
        this.storage.stop();
        this.executors.close();
    }

    @Test
    void opensTheDefaultH2FileAndRunsQueriesOnWorkers() {
        assertThat(this.dir.resolve("sakurabans.mv.db")).isRegularFile();
        final String thread = this.storage.read(dsl -> Thread.currentThread().getName()).join();
        assertThat(thread).startsWith("sakurabans-worker-");
        assertThat(this.storage.read(dsl -> dsl.fetchCount(SakuraTables.SCOPES)).join()).isOne();
    }

    @Test
    void failedWritesRollBack() {
        assertThatThrownBy(() -> this.storage.write(configuration -> {
            configuration.dsl().insertInto(SakuraTables.META)
                    .set(SakuraTables.META.K, "x").set(SakuraTables.META.V, "1").execute();
            throw new IllegalStateException("boom");
        }).join()).isInstanceOf(CompletionException.class);

        assertThat(new MetaRepository(this.storage).get("x").join()).isEmpty();
    }

    @Test
    void stoppedStorageFailsFutures() {
        this.storage.stop();

        assertThat(this.storage.running()).isFalse();
        assertThatThrownBy(() -> this.storage.read(dsl -> 1).join())
                .hasCauseInstanceOf(IllegalStateException.class);
    }

    @Test
    void metaValuesCanBeSetReplacedAndRemoved() {
        final MetaRepository meta = new MetaRepository(this.storage);

        meta.put("display_id_secret", "a").join();
        assertThat(meta.putIfAbsent("display_id_secret", "b").join()).isFalse();
        meta.put("display_id_secret", "c").join();
        assertThat(meta.get("display_id_secret").join()).contains("c");
        meta.remove("display_id_secret").join();
        assertThat(meta.get("display_id_secret").join()).isEmpty();
    }

    @Test
    void oneInstanceHoldsLeasesUntilTheyExpireOrAreReleased() {
        final Lease first = new Lease(this.storage, UUID.randomUUID(), this.clock);
        final Lease second = new Lease(this.storage, UUID.randomUUID(), this.clock);
        final Duration ttl = Duration.ofSeconds(30);

        assertThat(first.tryAcquire("sweeper", ttl).join()).isTrue();
        assertThat(second.tryAcquire("sweeper", ttl).join()).isFalse();
        assertThat(first.tryAcquire("sweeper", ttl).join()).as("renewal").isTrue();

        this.now.set(this.now.get().plus(Duration.ofSeconds(31)));
        assertThat(second.tryAcquire("sweeper", ttl).join()).as("expired").isTrue();
        assertThat(first.tryAcquire("sweeper", ttl).join()).isFalse();

        first.release("sweeper").join();
        assertThat(first.tryAcquire("sweeper", ttl).join()).as("not first's to release")
                .isFalse();
        second.release("sweeper").join();
        assertThat(first.tryAcquire("sweeper", ttl).join()).isTrue();
    }
}
