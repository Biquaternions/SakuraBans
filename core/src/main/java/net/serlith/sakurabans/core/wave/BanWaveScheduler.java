package net.serlith.sakurabans.core.wave;

import java.time.Duration;
import java.time.Instant;
import java.time.InstantSource;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.atomic.AtomicReference;
import net.kyori.adventure.text.logger.slf4j.ComponentLogger;
import net.serlith.sakurabans.api.model.Actor;
import net.serlith.sakurabans.api.wave.BanWave;
import net.serlith.sakurabans.core.concurrent.SakuraExecutors;
import net.serlith.sakurabans.core.storage.Lease;
import net.serlith.sakurabans.core.storage.repository.BanWaveRepository;
import net.serlith.sakurabans.core.util.Futures;
import org.jspecify.annotations.Nullable;

public final class BanWaveScheduler {

    static final String SOURCE = "scheduled";
    private static final String LEASE = "banwaves";
    private static final Duration INTERVAL = Duration.ofSeconds(30);
    private static final Duration LEASE_TTL = INTERVAL.multipliedBy(3);
    private static final Duration STUCK = Duration.ofMinutes(5);

    private final Lease lease;
    private final BanWaveRepository repository;
    private final SakuraBanWaveService waves;
    private final SakuraExecutors executors;
    private final InstantSource clock;
    private final ComponentLogger logger;
    private final AtomicReference<@Nullable ScheduledFuture<?>> task = new AtomicReference<>();

    public BanWaveScheduler(final Lease lease, final BanWaveRepository repository,
            final SakuraBanWaveService waves, final SakuraExecutors executors,
            final InstantSource clock, final ComponentLogger logger) {
        this.lease = lease;
        this.repository = repository;
        this.waves = waves;
        this.executors = executors;
        this.clock = clock;
        this.logger = logger;
    }

    public void start() {
        final ScheduledFuture<?> previous = this.task.getAndSet(this.executors.repeat(INTERVAL,
                INTERVAL, () -> Futures.logged(this.tick(), this.logger, "release ban waves")
                        .exceptionally(failure -> null).join()));
        if (previous != null) {
            previous.cancel(false);
        }
    }

    public void stop() {
        final ScheduledFuture<?> current = this.task.getAndSet(null);
        if (current != null) {
            current.cancel(false);
        }
    }

    public CompletableFuture<Void> tick() {
        final Instant now = this.clock.instant();
        return this.lease.tryAcquire(LEASE, LEASE_TTL).thenCompose(held -> {
            if (!held) {
                return CompletableFuture.<Void>completedFuture(null);
            }
            return this.repository.due(now, now.minus(STUCK)).thenCompose(due -> {
                CompletableFuture<Void> chain = CompletableFuture.completedFuture(null);
                for (final BanWave wave : due) {
                    final Actor actor = Actor.of(wave.createdBy().operator(), SOURCE);
                    chain = chain.thenCompose(ignored -> Futures.logged(this.waves.claim(
                                    wave.id(), Set.of(wave.status()), null, actor), this.logger,
                                    "release ban wave " + wave.id())
                            .handle((result, failure) -> null));
                }
                return chain;
            });
        });
    }

}
