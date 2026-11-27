package net.serlith.sakurabans.core.sync;

import java.time.Duration;
import java.time.Instant;
import java.time.InstantSource;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import net.serlith.sakurabans.api.model.Visibility;
import net.serlith.sakurabans.core.storage.DatabaseClock;
import net.serlith.sakurabans.core.storage.DatabaseTest;
import net.serlith.sakurabans.core.storage.dbo.SakuraTables;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Instances on one database. The integration tests run it on MariaDB, MySQL, PostgreSQL. Every
 * time the transport uses is the database's (S66), so the hosts' clocks are set minutes apart.
 */
class SqlPollingTransportTest extends DatabaseTest {

    private static final Duration SKEW = Duration.ofMinutes(10);

    private final List<SyncPacket> received = new CopyOnWriteArrayList<>();
    private final List<SyncPacket> receivedByFirst = new CopyOnWriteArrayList<>();
    private final AtomicInteger missed = new AtomicInteger();
    private SqlPollingTransport first;
    private SqlPollingTransport second;

    private SyncPacketHandler recorder(final List<SyncPacket> into) {
        return new SyncPacketHandler() {
            @Override
            public CompletableFuture<Void> handle(final SyncPacket packet) {
                into.add(packet);
                return CompletableFuture.completedFuture(null);
            }

            @Override
            public CompletableFuture<Void> missed() {
                SqlPollingTransportTest.this.missed.incrementAndGet();
                return CompletableFuture.completedFuture(null);
            }
        };
    }

    /** A transport on a host whose clock is {@code offset} away from the test's. */
    private SqlPollingTransport transport(final Duration offset) {
        final InstantSource host = () -> this.clock.instant().plus(offset);
        return new SqlPollingTransport(this.storage,
                new PacketCodec(UUID.randomUUID(), this.log.logger()), this.executors,
                this.config, new DatabaseClock(this.storage, host, this.log.logger()),
                this.log.logger());
    }

    @BeforeEach
    void instances() {
        // One host ahead of the database by minutes, the other behind it.
        this.first = this.transport(SKEW);
        this.second = this.transport(SKEW.negated());
        this.first.open(this.recorder(this.receivedByFirst));
        this.second.open(this.recorder(this.received));
    }

    private void send(final SqlPollingTransport from, final SyncPacket packet) {
        final int before = this.rows();
        from.send(packet);
        // send is asynchronous.
        eventually(() -> this.rows() > before);
    }

    private int rows() {
        return this.storage.read(dsl -> dsl.fetchCount(SakuraTables.SYNC_MESSAGES)).join();
    }

    private Instant databaseNow() {
        return this.storage.read(DatabaseClock::now).join();
    }

    /** A row written with a time of its own, as a late commit or an old message. */
    private void insert(final Instant createdAt, final SyncPacket packet) {
        this.storage.write(configuration -> configuration.dsl()
                .insertInto(SakuraTables.SYNC_MESSAGES)
                .set(SakuraTables.SYNC_MESSAGES.CREATED_AT, createdAt)
                .set(SakuraTables.SYNC_MESSAGES.PAYLOAD, new PacketCodec(UUID.randomUUID(),
                        this.log.logger()).encode(packet))
                .execute()).join();
    }

    @Test
    void otherInstancesReceiveEachPacketOnce() {
        final SyncPacket packet = new SyncPacket.PunishmentRevokePacket(5, Visibility.SILENT);
        this.send(this.first, packet);

        Assertions.assertThat(this.second.poll().join()).isOne();
        Assertions.assertThat(this.first.poll().join()).as("its own packet").isZero();
        // Still inside the overlap window, but already seen.
        Assertions.assertThat(this.second.poll().join()).isZero();
        Assertions.assertThat(this.received).containsExactly(packet);
    }

    @Test
    void hostsMinutesApartLoseNothingInEitherDirectionAndHandleNothingTwice() {
        for (long id = 1; id <= 3; id++) {
            this.send(this.first, SyncPacket.PunishmentEnforcePacket.of(id));
            this.send(this.second, SyncPacket.PunishmentEnforcePacket.of(100 + id));
            this.second.poll().join();
            this.first.poll().join();
        }
        this.second.poll().join();
        this.first.poll().join();

        Assertions.assertThat(this.received).extracting(packet ->
                ((SyncPacket.PunishmentEnforcePacket) packet).punishmentId())
                .containsExactly(1L, 2L, 3L);
        Assertions.assertThat(this.receivedByFirst).extracting(packet ->
                ((SyncPacket.PunishmentEnforcePacket) packet).punishmentId())
                .containsExactly(101L, 102L, 103L);
        Assertions.assertThat(this.missed).hasValue(0);
    }

    @Test
    void messagesAreStampedWithTheDatabasesTimeNotTheSenders() {
        final Instant before = this.databaseNow();
        this.send(this.first, SyncPacket.PunishmentEnforcePacket.of(1));
        final Instant after = this.databaseNow();

        final Instant stamped = this.read(dsl -> dsl.select(SakuraTables.SYNC_MESSAGES.CREATED_AT)
                .from(SakuraTables.SYNC_MESSAGES)
                .fetchSingle(SakuraTables.SYNC_MESSAGES.CREATED_AT));
        // PostgreSQL's CURRENT_TIMESTAMP is the transaction's start, so allow a little slack.
        Assertions.assertThat(stamped).isBetween(before.minusSeconds(1), after.plusSeconds(1));
    }

    @Test
    void rowsCommittedLateAreNotMissed() {
        this.second.poll().join();
        // Written with a time from before that poll, as if its transaction committed late.
        this.insert(this.databaseNow().minusSeconds(5), SyncPacket.PunishmentEnforcePacket.of(9));

        Assertions.assertThat(this.second.poll().join()).isOne();
        Assertions.assertThat(this.received)
                .containsExactly(SyncPacket.PunishmentEnforcePacket.of(9));
    }

    @Test
    void newInstancesDontReplayHistory() {
        this.insert(this.databaseNow().minus(SqlPollingTransport.OVERLAP).minusSeconds(30),
                SyncPacket.PunishmentEnforcePacket.of(1));
        final List<SyncPacket> late = new CopyOnWriteArrayList<>();
        final SqlPollingTransport third = this.transport(Duration.ofHours(-3));
        third.open(this.recorder(late));

        Assertions.assertThat(third.poll().join()).isZero();
        Assertions.assertThat(late).isEmpty();
    }

    @Test
    void packetsAreHandledInOrder() {
        for (long id = 1; id <= 5; id++) {
            this.send(this.first, SyncPacket.PunishmentEnforcePacket.of(id));
        }

        this.second.poll().join();

        Assertions.assertThat(this.received).extracting(packet ->
                ((SyncPacket.PunishmentEnforcePacket) packet).punishmentId())
                .containsExactly(1L, 2L, 3L, 4L, 5L);
    }

    @Test
    void cleanupDeletesOnlyRowsOldByTheDatabasesClockWhateverTheHostsSay() {
        final Instant now = this.databaseNow();
        this.insert(now.minus(SqlPollingTransport.MAX_AGE).minusSeconds(30),
                SyncPacket.PunishmentEnforcePacket.of(1));
        // Older than MAX_AGE by the clock of the host that is ahead, younger by the database's.
        this.insert(now.minus(SqlPollingTransport.MAX_AGE).plusSeconds(60),
                SyncPacket.PunishmentEnforcePacket.of(2));
        this.send(this.first, SyncPacket.PunishmentEnforcePacket.of(3));

        Assertions.assertThat(this.first.cleanup().join()).as("host ahead").isOne();
        Assertions.assertThat(this.second.cleanup().join()).as("host behind").isZero();
        Assertions.assertThat(this.rows()).isEqualTo(2);
    }

    @Test
    void longGapsMeanPacketsMayHaveBeenMissed() {
        this.second.poll().join();
        Assertions.assertThat(this.missed).hasValue(0);

        this.second.rewind(SqlPollingTransport.MISSED_AFTER.plusSeconds(1));
        this.second.poll().join();
        Assertions.assertThat(this.missed).hasValue(1);

        this.second.poll().join();
        Assertions.assertThat(this.missed).hasValue(1);
    }
}
