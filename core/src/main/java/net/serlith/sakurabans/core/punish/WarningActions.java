package net.serlith.sakurabans.core.punish;

import java.time.InstantSource;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import net.kyori.adventure.text.logger.slf4j.ComponentLogger;
import net.serlith.sakurabans.api.event.EventPriority;
import net.serlith.sakurabans.api.event.PunishmentCreatedEvent;
import net.serlith.sakurabans.api.event.Subscription;
import net.serlith.sakurabans.api.model.CompositeVictim;
import net.serlith.sakurabans.api.model.PlayerVictim;
import net.serlith.sakurabans.api.model.Punishment;
import net.serlith.sakurabans.api.model.PunishmentType;
import net.serlith.sakurabans.core.configuration.ConfigService;
import net.serlith.sakurabans.core.event.SakuraEventBus;
import net.serlith.sakurabans.core.platform.Platform;
import net.serlith.sakurabans.core.player.NameCache;
import net.serlith.sakurabans.core.storage.repository.PunishmentRepository;
import net.serlith.sakurabans.core.util.Futures;
import org.jspecify.annotations.Nullable;

public final class WarningActions {

    private final SakuraEventBus events;
    private final PunishmentRepository punishments;
    private final ConfigService config;
    private final Platform platform;
    private final NameCache names;
    private final InstantSource clock;
    private final ComponentLogger logger;
    private final AtomicReference<@Nullable Subscription> subscription = new AtomicReference<>();

    public WarningActions(final SakuraEventBus events, final PunishmentRepository punishments,
            final ConfigService config, final Platform platform, final NameCache names,
            final InstantSource clock, final ComponentLogger logger) {
        this.events = events;
        this.punishments = punishments;
        this.config = config;
        this.platform = platform;
        this.names = names;
        this.clock = clock;
        this.logger = logger;
    }

    public void start() {
        final Subscription previous = this.subscription.getAndSet(this.events.subscribe(
                PunishmentCreatedEvent.class, EventPriority.MONITOR, this::created));
        if (previous != null) {
            previous.unsubscribe();
        }
    }

    public void stop() {
        final Subscription current = this.subscription.getAndSet(null);
        if (current != null) {
            current.unsubscribe();
        }
    }

    private void created(final PunishmentCreatedEvent event) {
        final Punishment punishment = event.punishment();
        if (punishment.type() != PunishmentType.WARN) {
            return;
        }
        final Map<Integer, List<String>> actions = this.config.settings().warnings().actions();
        final UUID account = switch (punishment.victim()) {
            case PlayerVictim player -> player.uuid();
            case CompositeVictim composite -> composite.uuid();
            default -> null;
        };
        if (actions.isEmpty() || account == null) {
            return;
        }
        // Up to this warning: one issued meanwhile must not make this one reach its number.
        Futures.logged(this.punishments.activeWarnings(account, punishment.id(),
                        this.clock.instant())
                .thenAccept(count -> this.reached(punishment, count, actions)), this.logger,
                "run the warning actions for " + account);
    }

    private void reached(final Punishment punishment, final long count,
            final Map<Integer, List<String>> actions) {
        final List<String> commands = actions.get((int) Math.min(count, Integer.MAX_VALUE));
        if (commands == null || commands.isEmpty()) {
            return;
        }
        final Map<String, String> values =
                new HashMap<>(ConsoleActions.values(punishment, this.names::name));
        values.put("count", Long.toString(count));
        ConsoleActions.run(this.platform, commands, values, this.logger,
                count + " warnings");
    }
}
