package net.serlith.sakurabans.core.punish;

import com.google.common.base.Preconditions;
import java.time.Duration;
import java.time.Instant;
import java.time.InstantSource;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import net.kyori.adventure.text.logger.slf4j.ComponentLogger;
import net.serlith.sakurabans.api.event.EventPriority;
import net.serlith.sakurabans.api.event.PunishmentExpungedEvent;
import net.serlith.sakurabans.api.event.PunishmentVoidedEvent;
import net.serlith.sakurabans.api.event.Subscription;
import net.serlith.sakurabans.api.model.Punishment;
import net.serlith.sakurabans.core.concurrent.SakuraExecutors;
import net.serlith.sakurabans.core.configuration.ConfigService;
import net.serlith.sakurabans.core.event.SakuraEventBus;
import net.serlith.sakurabans.core.message.NetworkTotals;
import net.serlith.sakurabans.core.message.Placeholders;
import net.serlith.sakurabans.core.model.PlayerCounts;
import net.serlith.sakurabans.core.model.PunishmentTotals;
import net.serlith.sakurabans.core.storage.repository.PunishmentRepository;
import net.serlith.sakurabans.core.util.Futures;
import org.jspecify.annotations.Nullable;

public final class PunishmentCounts {

    static final Duration TICK = Duration.ofSeconds(5);

    private final Reads reads;
    private final NetworkTotals totals;
    private final SakuraEventBus events;
    private final ConfigService config;
    private final SakuraExecutors executors;
    private final InstantSource clock;
    private final ComponentLogger logger;
    private final ConcurrentMap<UUID, Slot> players = new ConcurrentHashMap<>();
    private final AtomicReference<@Nullable ScheduledFuture<?>> task =
            new AtomicReference<>();
    private final List<Subscription> subscriptions = new CopyOnWriteArrayList<>();
    private final AtomicReference<@Nullable Instant> lastRefresh = new AtomicReference<>();

    private static final class Slot {
        private final AtomicLong fills = new AtomicLong();
        private volatile @Nullable PlayerCounts counts;
    }

    interface Reads {

        CompletableFuture<PunishmentTotals> totals(Instant now);

        CompletableFuture<PlayerCounts> countsOf(UUID uuid, Instant now);
    }

    public PunishmentCounts(final PunishmentRepository punishments, final NetworkTotals totals,
            final SakuraEventBus events, final ConfigService config,
            final SakuraExecutors executors, final InstantSource clock,
            final ComponentLogger logger) {
        this(new Reads() {
            @Override
            public CompletableFuture<PunishmentTotals> totals(final Instant now) {
                return punishments.totals(now);
            }

            @Override
            public CompletableFuture<PlayerCounts> countsOf(final UUID uuid, final Instant now) {
                return punishments.countsOf(uuid, now);
            }
        }, totals, events, config, executors, clock, logger);
    }

    PunishmentCounts(final Reads reads, final NetworkTotals totals, final SakuraEventBus events,
            final ConfigService config, final SakuraExecutors executors,
            final InstantSource clock, final ComponentLogger logger) {
        this.reads = reads;
        this.totals = totals;
        this.events = events;
        this.config = config;
        this.executors = executors;
        this.clock = clock;
        this.logger = logger;
    }

    public void start() {
        final ScheduledFuture<?> previous = this.task.getAndSet(this.executors.repeat(
                Duration.ZERO, TICK, () -> Futures.logged(this.refreshIfDue(), this.logger,
                        "count the network's punishments").exceptionally(failure -> null)
                        .join()));
        if (previous != null) {
            previous.cancel(false);
        }
        this.subscriptions.add(this.events.subscribe(PunishmentVoidedEvent.class,
                EventPriority.MONITOR, event -> this.recount(event.punishment())));
        this.subscriptions.add(this.events.subscribe(PunishmentExpungedEvent.class,
                EventPriority.MONITOR, event -> this.recount(event.punishment())));
    }

    private void recount(final Punishment punishment) {
        Futures.logged(this.changed(punishment), this.logger,
                "count the punishments of the victim of " + punishment.id());
    }

    public void stop() {
        this.subscriptions.forEach(Subscription::unsubscribe);
        this.subscriptions.clear();
        final ScheduledFuture<?> current = this.task.getAndSet(null);
        if (current != null) {
            current.cancel(false);
        }
    }

    public CompletableFuture<Void> refreshIfDue() {
        final Instant now = this.clock.instant();
        final Instant last = this.lastRefresh.get();
        final Duration refresh = this.config.settings().placeholders().refresh();
        // The compare-and-set keeps two calls from reading at once.
        if ((last != null && now.isBefore(last.plus(refresh)))
                || !this.lastRefresh.compareAndSet(last, now)) {
            return CompletableFuture.completedFuture(null);
        }
        return this.refreshTotals().whenComplete((ignored, failure) -> {
            if (failure != null) {
                this.lastRefresh.compareAndSet(now, last);
            }
        });
    }

    public CompletableFuture<Void> refreshTotals() {
        return this.reads.totals(this.clock.instant()).thenAccept(this.totals::update);
    }

    public Optional<PunishmentTotals> totals() {
        return this.totals.current();
    }

    public CompletableFuture<Void> load(final UUID uuid) {
        Preconditions.checkNotNull(uuid, "uuid");
        final Slot slot = new Slot();
        this.players.put(uuid, slot);
        return this.fill(uuid, slot);
    }

    public CompletableFuture<Void> reload(final UUID uuid) {
        final Slot slot = this.players.get(uuid);
        return slot == null ? CompletableFuture.completedFuture(null) : this.fill(uuid, slot);
    }

    public CompletableFuture<Void> reloadAll() {
        final List<CompletableFuture<Void>> loads = new ArrayList<>();
        this.players.forEach((uuid, slot) -> loads.add(this.fill(uuid, slot)));
        return CompletableFuture.allOf(loads.toArray(CompletableFuture[]::new));
    }

    public CompletableFuture<Void> changed(final Punishment punishment) {
        return Placeholders.countedPlayer(punishment)
                .map(this::reload)
                .orElseGet(() -> CompletableFuture.completedFuture(null));
    }

    public void forget(final UUID uuid) {
        this.players.remove(uuid);
    }

    public Optional<PlayerCounts> cached(final UUID uuid) {
        final Slot slot = this.players.get(uuid);
        return slot == null ? Optional.empty() : Optional.ofNullable(slot.counts);
    }

    public CompletableFuture<Optional<PlayerCounts>> fetch(final UUID uuid) {
        final Optional<PlayerCounts> cached = this.cached(uuid);
        if (cached.isPresent()) {
            return CompletableFuture.completedFuture(cached);
        }
        return this.reads.countsOf(uuid, this.clock.instant())
                .thenApply(Optional::of)
                .exceptionally(failure -> {
                    this.logger.warn("Couldn't count the punishments of {}", uuid, failure);
                    return Optional.empty();
                });
    }

    private CompletableFuture<Void> fill(final UUID uuid, final Slot slot) {
        final long fill = slot.fills.incrementAndGet();
        return this.reads.countsOf(uuid, this.clock.instant()).thenAccept(counts -> {
            // Checked and set together, so a newer fill's answer always lands after this one.
            synchronized (slot) {
                if (this.players.get(uuid) == slot && slot.fills.get() == fill) {
                    slot.counts = counts;
                }
            }
        });
    }
}
