package net.serlith.sakurabans.core.punish;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import net.kyori.adventure.text.logger.slf4j.ComponentLogger;
import net.serlith.sakurabans.api.event.EventPriority;
import net.serlith.sakurabans.api.event.PunishmentCreatedEvent;
import net.serlith.sakurabans.api.event.Subscription;
import net.serlith.sakurabans.api.model.Punishment;
import net.serlith.sakurabans.api.model.TemplateRef;
import net.serlith.sakurabans.api.template.TemplateInfo;
import net.serlith.sakurabans.core.event.SakuraEventBus;
import net.serlith.sakurabans.core.event.SakuraTemplateStepEvent;
import net.serlith.sakurabans.core.platform.Platform;
import net.serlith.sakurabans.core.player.NameCache;
import net.serlith.sakurabans.core.template.SakuraTemplateService;
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
        this.events.post(new SakuraTemplateStepEvent(punishment, ref));
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
        final Map<String, String> values = new HashMap<>(
                ConsoleActions.values(punishment, this.names::name));
        values.put("template", ref.name());
        values.put("step", Integer.toString(ref.step() + 1));
        ConsoleActions.run(this.platform, commands, values, this.logger,
                "template " + ref.name());
    }

}
