package net.serlith.sakurabans.core.sync;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import net.serlith.sakurabans.api.model.Visibility;
import net.serlith.sakurabans.core.storage.DatabaseTest;
import net.serlith.sakurabans.core.storage.dbo.SakuraTables;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Two instances on one database. The integration tests run it on MariaDB, MySQL, PostgreSQL. */
class SqlPollingTransportTest extends DatabaseTest {

    private final List<SyncPacket> received = new CopyOnWriteArrayList<>();
    private final AtomicInteger missed = new AtomicInteger();
    private SqlPollingTransport first;
    private SqlPollingTransport second;

    private final SyncPacketHandler recorder = new SyncPacketHandler() {
        @Override
        public CompletableFuture<Void> handle(final SyncPacket packet) {
            SqlPollingTransportTest.this.received.add(packet);
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public CompletableFuture<Void> missed() {
            SqlPollingTransportTest.this.missed.incrementAndGet();
            return CompletableFuture.completedFuture(null);
        }
    };

    private SqlPollingTransport transport() {
        return new SqlPollingTransport(this.storage,
                new PacketCodec(UUID.randomUUID(), this.log.logger()), this.executors,
                this.config, this.clock, this.log.logger());
    }

    @BeforeEach
    void instances() {
        this.first = this.transport();
        this.second = this.transport();
        this.first.open(packet -> CompletableFuture.failedFuture(
                new AssertionError("own packet " + packet)));
        this.second.open(this.recorder);
    }

    /** Waits for the insert: {@code send} is asynchronous. */
    private void send(final SyncPacket packet) {
        this.first.send(packet);
        this.awaitRows(1);
    }

    private void awaitRows(final int atLeast) {
        eventually(() -> this.rows() >= atLeast);
    }

    private int rows() {
        return this.storage.read(dsl -> dsl.fetchCount(SakuraTables.SYNC_MESSAGES)).join();
    }

    private void advance(final Duration duration) {
        this.now.set(this.now.get().plus(duration));
    }

    @Test
    void otherInstancesReceiveEachPacketOnce() {
        final SyncPacket packet = new SyncPacket.PunishmentRevokePacket(5, Visibility.SILENT);
        this.send(packet);
        this.advance(Duration.ofSeconds(2));

        Assertions.assertThat(this.second.poll().join()).isOne();
        Assertions.assertThat(this.first.poll().join()).isZero();
        this.advance(Duration.ofSeconds(2));
        // Still inside the overlap window, but already seen.
        Assertions.assertThat(this.second.poll().join()).isZero();
        Assertions.assertThat(this.received).containsExactly(packet);
    }

    @Test
    void rowsCommittedLateAreNotMissed() {
        this.advance(Duration.ofSeconds(10));
        this.second.poll().join();
        // Written with a time from before that poll, as if its transaction committed late.
        final Instant late = this.now.get().minus(Duration.ofSeconds(5));
        this.storage.write(configuration -> configuration.dsl()
                .insertInto(SakuraTables.SYNC_MESSAGES)
                .set(SakuraTables.SYNC_MESSAGES.CREATED_AT, late)
                .set(SakuraTables.SYNC_MESSAGES.PAYLOAD, new PacketCodec(UUID.randomUUID(),
                        this.log.logger()).encode(SyncPacket.PunishmentEnforcePacket.of(9)))
                .execute()).join();
        this.advance(Duration.ofSeconds(2));

        Assertions.assertThat(this.second.poll().join()).isOne();
        Assertions.assertThat(this.received)
                .containsExactly(SyncPacket.PunishmentEnforcePacket.of(9));
    }

    @Test
    void packetsAreHandledInOrder() {
        for (long id = 1; id <= 5; id++) {
            this.first.send(SyncPacket.PunishmentEnforcePacket.of(id));
            this.awaitRows((int) id);
        }
        this.advance(Duration.ofSeconds(2));

        this.second.poll().join();

        Assertions.assertThat(this.received).extracting(packet ->
                ((SyncPacket.PunishmentEnforcePacket) packet).punishmentId())
                .containsExactly(1L, 2L, 3L, 4L, 5L);
    }

    @Test
    void cleanupDeletesOnlyOldRows() {
        this.send(SyncPacket.PunishmentEnforcePacket.of(1));
        this.advance(SqlPollingTransport.MAX_AGE.plusSeconds(1));
        this.first.send(SyncPacket.PunishmentEnforcePacket.of(2));
        this.awaitRows(2);

        Assertions.assertThat(this.first.cleanup(this.now.get()).join()).isOne();
        Assertions.assertThat(this.rows()).isOne();
    }

    @Test
    void longGapsMeanPacketsMayHaveBeenMissed() {
        this.advance(Duration.ofSeconds(30));
        this.second.poll().join();
        Assertions.assertThat(this.missed).hasValue(0);

        this.advance(SqlPollingTransport.MISSED_AFTER.plusSeconds(1));
        this.second.poll().join();
        Assertions.assertThat(this.missed).hasValue(1);
    }
}
