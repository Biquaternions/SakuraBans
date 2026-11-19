package net.serlith.sakurabans.core.storage;

import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.CompletionException;
import net.serlith.sakurabans.core.storage.dbo.SakuraTables;
import net.serlith.sakurabans.core.storage.repository.MetaRepository;
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
    void oneInstanceHoldsLeasesUntilTheyExpireOrAreReleased() {
        final Lease first = new Lease(this.storage, UUID.randomUUID(), this.clock);
        final Lease second = new Lease(this.storage, UUID.randomUUID(), this.clock);
        final Duration ttl = Duration.ofSeconds(30);

        Assertions.assertThat(first.tryAcquire("sweeper", ttl).join()).isTrue();
        Assertions.assertThat(second.tryAcquire("sweeper", ttl).join()).isFalse();
        Assertions.assertThat(first.tryAcquire("sweeper", ttl).join()).as("renewal").isTrue();

        this.now.set(this.now.get().plus(Duration.ofSeconds(31)));
        Assertions.assertThat(second.tryAcquire("sweeper", ttl).join()).as("expired").isTrue();
        Assertions.assertThat(first.tryAcquire("sweeper", ttl).join()).isFalse();

        first.release("sweeper").join();
        Assertions.assertThat(first.tryAcquire("sweeper", ttl).join()).as("not first's to release")
                .isFalse();
        second.release("sweeper").join();
        Assertions.assertThat(first.tryAcquire("sweeper", ttl).join()).isTrue();
    }
}
