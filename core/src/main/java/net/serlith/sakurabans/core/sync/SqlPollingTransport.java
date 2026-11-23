package net.serlith.sakurabans.core.sync;

import java.time.Duration;
import java.time.Instant;
import java.time.InstantSource;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.atomic.AtomicReference;
import net.kyori.adventure.text.logger.slf4j.ComponentLogger;
import net.serlith.sakurabans.core.concurrent.SakuraExecutors;
import net.serlith.sakurabans.core.configuration.ConfigService;
import net.serlith.sakurabans.core.storage.StorageService;
import net.serlith.sakurabans.core.storage.dbo.SakuraTables;
import net.serlith.sakurabans.core.util.Futures;
import org.jspecify.annotations.Nullable;

public final class SqlPollingTransport implements SyncTransport {

    static final Duration OVERLAP = Duration.ofSeconds(30);
    static final Duration MAX_AGE = Duration.ofMinutes(5);
    static final Duration MISSED_AFTER = Duration.ofMinutes(2);

    private final StorageService storage;
    private final PacketCodec codec;
    private final SakuraExecutors executors;
    private final ConfigService config;
    private final InstantSource clock;
    private final ComponentLogger logger;
    private final Map<Long, Instant> seen = new ConcurrentHashMap<>();
    private final AtomicReference<@Nullable ScheduledFuture<?>> task = new AtomicReference<>();
    private volatile @Nullable SyncPacketHandler handler;
    private volatile Instant cursor = Instant.EPOCH;
    private volatile Instant lastSuccess = Instant.EPOCH;

    public SqlPollingTransport(final StorageService storage, final PacketCodec codec,
            final SakuraExecutors executors, final ConfigService config,
            final InstantSource clock, final ComponentLogger logger) {
        this.storage = storage;
        this.codec = codec;
        this.executors = executors;
        this.config = config;
        this.clock = clock;
        this.logger = logger;
    }

    @Override
    public void send(final SyncPacket packet) {
        final byte[] payload = this.codec.encode(packet);
        Futures.logged(this.storage.write(configuration -> configuration.dsl()
                .insertInto(SakuraTables.SYNC_MESSAGES)
                .set(SakuraTables.SYNC_MESSAGES.CREATED_AT, this.clock.instant())
                .set(SakuraTables.SYNC_MESSAGES.PAYLOAD, payload)
                .execute()), this.logger, "publish " + packet);
    }

    @Override
    public void start(final SyncPacketHandler packets) {
        this.open(packets);
        final Duration interval = this.config.storage().sync().pollInterval();
        final ScheduledFuture<?> previous = this.task.getAndSet(this.executors.repeat(interval,
                interval, () -> Futures.logged(this.poll(), this.logger, "poll sync messages")
                        .exceptionally(failure -> 0).join()));
        if (previous != null) {
            previous.cancel(false);
        }
    }

    void open(final SyncPacketHandler packets) {
        this.handler = packets;
        final Instant now = this.clock.instant();
        this.cursor = now;
        this.lastSuccess = now;
    }

    @Override
    public void stop() {
        final ScheduledFuture<?> current = this.task.getAndSet(null);
        if (current != null) {
            current.cancel(false);
        }
    }

    public CompletableFuture<Integer> poll() {
        final SyncPacketHandler packets = this.handler;
        if (packets == null) {
            return CompletableFuture.completedFuture(0);
        }
        final Instant started = this.clock.instant();
        final boolean missed = Duration.between(this.lastSuccess, started)
                .compareTo(MISSED_AFTER) > 0;
        final Instant from = this.cursor.minus(OVERLAP);
        return this.storage.read(dsl -> dsl
                        .select(SakuraTables.SYNC_MESSAGES.ID,
                                SakuraTables.SYNC_MESSAGES.CREATED_AT,
                                SakuraTables.SYNC_MESSAGES.PAYLOAD)
                        .from(SakuraTables.SYNC_MESSAGES)
                        .where(SakuraTables.SYNC_MESSAGES.CREATED_AT.gt(from))
                        .orderBy(SakuraTables.SYNC_MESSAGES.ID)
                        .fetch(row -> new Row(row.value1(), row.value2(), row.value3())))
                .thenCompose(rows -> this.handleAll(packets, rows))
                .thenApply(handled -> {
                    this.cursor = started;
                    this.lastSuccess = started;
                    // The next poll reads from started - OVERLAP; older ids can't come back.
                    final Instant oldest = started.minus(OVERLAP);
                    this.seen.values().removeIf(createdAt -> !createdAt.isAfter(oldest));
                    if (missed) {
                        this.logger.warn("No sync poll succeeded for over {} minutes; reloading"
                                + " the state of online players", MISSED_AFTER.toMinutes());
                        Futures.logged(packets.missed(), this.logger,
                                "catch up after missed sync messages");
                    }
                    return handled;
                });
    }

    private record Row(long id, Instant createdAt, byte[] payload) {
    }

    private CompletableFuture<Integer> handleAll(final SyncPacketHandler packets,
            final List<Row> rows) {
        CompletableFuture<Integer> chain = CompletableFuture.completedFuture(0);
        for (final Row row : rows) {
            if (this.seen.putIfAbsent(row.id(), row.createdAt()) != null) {
                continue;
            }
            final SyncPacket packet = this.codec.decode(row.payload()).orElse(null);
            if (packet == null) {
                continue;
            }
            // In id order, and one failing packet doesn't stop the rest.
            chain = chain.thenCompose(count -> Futures.logged(packets.handle(packet),
                            this.logger, "apply " + packet)
                    .handle((ignored, failure) -> count + 1));
        }
        return chain;
    }

    @Override
    public CompletableFuture<Integer> cleanup(final Instant now) {
        return this.storage.write(configuration -> configuration.dsl()
                .deleteFrom(SakuraTables.SYNC_MESSAGES)
                .where(SakuraTables.SYNC_MESSAGES.CREATED_AT.lt(now.minus(MAX_AGE)))
                .execute());
    }

}
