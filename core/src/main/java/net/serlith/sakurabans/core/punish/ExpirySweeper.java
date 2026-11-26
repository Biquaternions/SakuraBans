package net.serlith.sakurabans.core.punish;

import java.time.Duration;
import java.time.Instant;
import java.time.InstantSource;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;
import net.kyori.adventure.text.logger.slf4j.ComponentLogger;
import net.serlith.sakurabans.api.model.EndKind;
import net.serlith.sakurabans.api.model.Punishment;
import net.serlith.sakurabans.core.concurrent.SakuraExecutors;
import net.serlith.sakurabans.core.event.SakuraEventBus;
import net.serlith.sakurabans.core.event.SakuraPunishmentExpiredEvent;
import net.serlith.sakurabans.core.platform.OnlinePlayer;
import net.serlith.sakurabans.core.platform.Platform;
import net.serlith.sakurabans.core.storage.Lease;
import net.serlith.sakurabans.core.storage.repository.PunishmentRepository;
import net.serlith.sakurabans.core.sync.SyncService;
import net.serlith.sakurabans.core.util.Futures;
import org.jspecify.annotations.Nullable;

public final class ExpirySweeper {

    private static final String LEASE = "sweeper";
    private static final Duration INTERVAL = Duration.ofSeconds(30);
    private static final Duration LEASE_TTL = INTERVAL.multipliedBy(3);
    private static final Duration UNJOINED_GRACE = Duration.ofMinutes(1);
    private static final Duration MUTE_REFRESH = Duration.ofMinutes(1);
    private static final int BATCH = 500;

    private final Lease lease;
    private final PunishmentRepository punishments;
    private final MuteCache mutes;
    private final Enforcer enforcer;
    private final SyncService sync;
    private final SakuraEventBus events;
    private final SakuraExecutors executors;
    private final Platform platform;
    private final InstantSource clock;
    private final ComponentLogger logger;
    private final AtomicReference<@Nullable ScheduledFuture<?>> task = new AtomicReference<>();

    public ExpirySweeper(final Lease lease, final PunishmentRepository punishments,
            final MuteCache mutes, final Enforcer enforcer, final SyncService sync,
            final SakuraEventBus events,
            final SakuraExecutors executors, final Platform platform, final InstantSource clock,
            final ComponentLogger logger) {
        this.lease = lease;
        this.punishments = punishments;
        this.mutes = mutes;
        this.enforcer = enforcer;
        this.sync = sync;
        this.events = events;
        this.executors = executors;
        this.platform = platform;
        this.clock = clock;
        this.logger = logger;
    }

    public void start() {
        final ScheduledFuture<?> previous = this.task.getAndSet(this.executors.repeat(INTERVAL,
                INTERVAL, () -> Futures.logged(this.sweep(), this.logger, "sweep expired"
                        + " punishments").exceptionally(failure -> 0).join()));
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

    public CompletableFuture<Integer> sweep() {
        final Instant now = this.clock.instant();
        final Set<UUID> online = this.platform.onlinePlayers().stream()
                .map(OnlinePlayer::uuid)
                .collect(Collectors.toSet());
        this.mutes.retainOnly(online, now.minus(UNJOINED_GRACE));
        Futures.logged(this.mutes.refreshLoadedBefore(now.minus(MUTE_REFRESH)), this.logger,
                "refresh cached mutes");
        return this.lease.tryAcquire(LEASE, LEASE_TTL).thenCompose(held -> {
            if (!held) {
                return CompletableFuture.completedFuture(0);
            }
            // Under the lease, so one instance deletes old sync messages
            Futures.logged(this.sync.cleanup(now), this.logger, "delete old sync messages");
            return this.punishments.expiredActive(now, BATCH).thenCompose(this::expire);
        });
    }

    private CompletableFuture<Integer> expire(final List<Punishment> due) {
        final List<CompletableFuture<Optional<Punishment>>> ends = due.stream()
                .map(punishment -> this.punishments.end(punishment.id(), EndKind.EXPIRED,
                        punishment.expiresAt().orElseThrow(), null, null))
                .toList();
        return Futures.allOf(ends).thenApply(results -> {
            int ended = 0;
            for (final Optional<Punishment> result : results) {
                if (result.isEmpty()) {
                    // Revoked meanwhile.
                    continue;
                }
                ended++;
                Futures.logged(this.enforcer.ended(result.get()), this.logger,
                        "apply an expiry");
                this.events.post(new SakuraPunishmentExpiredEvent(result.get()));
            }
            return ended;
        });
    }

}
