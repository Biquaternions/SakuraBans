package net.serlith.sakurabans.core.template;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import net.kyori.adventure.text.logger.slf4j.ComponentLogger;
import net.serlith.sakurabans.api.event.EventPriority;
import net.serlith.sakurabans.api.event.PunishmentCreatedEvent;
import net.serlith.sakurabans.api.event.Subscription;
import net.serlith.sakurabans.api.model.AddressRangeVictim;
import net.serlith.sakurabans.api.model.AddressVictim;
import net.serlith.sakurabans.api.model.CompositeVictim;
import net.serlith.sakurabans.api.model.ConsoleOperator;
import net.serlith.sakurabans.api.model.CustomOperator;
import net.serlith.sakurabans.api.model.PlayerOperator;
import net.serlith.sakurabans.api.model.PlayerVictim;
import net.serlith.sakurabans.api.model.Punishment;
import net.serlith.sakurabans.api.model.TemplateRef;
import net.serlith.sakurabans.api.template.TemplateInfo;
import net.serlith.sakurabans.core.event.SakuraEventBus;
import net.serlith.sakurabans.core.event.SakuraTemplateStepEvent;
import net.serlith.sakurabans.core.platform.Platform;
import net.serlith.sakurabans.core.player.NameCache;
import net.serlith.sakurabans.core.util.Durations;
import org.jspecify.annotations.Nullable;

public final class TemplateActions {

    private final SakuraEventBus events;
    private final SakuraTemplateService templates;
    private final Platform platform;
    private final NameCache names;
    private final ComponentLogger logger;
    private final AtomicReference<@Nullable Subscription> subscription = new AtomicReference<>();

    public TemplateActions(final SakuraEventBus events, final SakuraTemplateService templates,
            final Platform platform, final NameCache names, final ComponentLogger logger) {
        this.events = events;
        this.templates = templates;
        this.platform = platform;
        this.names = names;
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
        final TemplateRef ref = punishment.template().orElse(null);
        if (ref == null) {
            return;
        }
        this.events.post(new SakuraTemplateStepEvent(punishment, ref, event.remote()));
        if (event.remote()) {
            return;
        }
        final TemplateInfo template = this.templates.template(ref.name()).orElse(null);
        if (template == null) {
            return;
        }
        final List<String> commands = new ArrayList<>(template.actions());
        if (ref.step() < template.ladder().size()) {
            commands.addAll(template.ladder().get(ref.step()).actions());
        }
        if (commands.isEmpty()) {
            return;
        }
        final Map<String, String> values = this.values(punishment, ref);
        for (final String command : commands) {
            final String filled = fill(command, values);
            try {
                this.platform.dispatchConsoleCommand(filled);
            } catch (final RuntimeException e) {
                this.logger.warn("The action '{}' of template {} failed", filled, ref.name(), e);
            }
        }
    }

    private Map<String, String> values(final Punishment punishment, final TemplateRef ref) {
        final String victim = switch (punishment.victim()) {
            case PlayerVictim player -> this.names.name(player.uuid())
                    .orElse(player.uuid().toString());
            case CompositeVictim composite -> this.names.name(composite.uuid())
                    .orElse(composite.uuid().toString());
            case AddressVictim address -> address.address().getHostAddress();
            case AddressRangeVictim range -> range.network().getHostAddress() + "/"
                    + range.prefixLength();
        };
        final String uuid = switch (punishment.victim()) {
            case PlayerVictim player -> player.uuid().toString();
            case CompositeVictim composite -> composite.uuid().toString();
            default -> "";
        };
        final String address = switch (punishment.victim()) {
            case AddressVictim single -> single.address().getHostAddress();
            case CompositeVictim composite -> composite.address().getHostAddress();
            default -> "";
        };
        final String operator = switch (punishment.operator()) {
            case ConsoleOperator console -> "Console";
            case PlayerOperator player -> player.name();
            case CustomOperator custom -> custom.name();
        };
        final Optional<Duration> duration = punishment.expiresAt()
                .map(expiry -> Duration.between(punishment.createdAt(), expiry));
        return Map.of(
                "victim", victim,
                "uuid", uuid,
                "address", address,
                "id", Long.toString(punishment.id()),
                "operator", operator,
                "reason", punishment.reason(),
                "template", ref.name(),
                "step", Integer.toString(ref.step() + 1),
                "duration", duration.map(value -> Durations.formatCompact(value)
                        .replace(" ", "")).orElse("permanent"));
    }

    static String fill(final String command, final Map<String, String> values) {
        String filled = command;
        for (final Map.Entry<String, String> value : values.entrySet()) {
            filled = filled.replace("{" + value.getKey() + "}",
                    value.getValue().replaceAll("\\R", " "));
        }
        return filled;
    }

}
