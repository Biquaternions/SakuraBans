package net.serlith.sakurabans.core.punish;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.kyori.adventure.text.logger.slf4j.ComponentLogger;
import net.serlith.sakurabans.api.event.TemplateAppliedEvent;
import net.serlith.sakurabans.api.model.TemplateRef;
import net.serlith.sakurabans.api.template.LadderTemplate;
import net.serlith.sakurabans.api.template.TemplateInfo;
import net.serlith.sakurabans.core.platform.Platform;
import net.serlith.sakurabans.core.player.NameCache;

/**
 * A template's console actions. {@link SakuraPunishmentService} runs them after a use is stored,
 * right after posting its {@link TemplateAppliedEvent} (S64; they used to run from a
 * {@code MONITOR} subscription to that event, so the order is unchanged).
 */
public final class TemplateActions {

    private final Platform platform;
    private final NameCache names;
    private final ComponentLogger logger;

    public TemplateActions(final Platform platform, final NameCache names,
            final ComponentLogger logger) {
        this.platform = platform;
        this.names = names;
        this.logger = logger;
    }

    /**
     * The template's actions, then the step's. {@code template} is the definition that was
     * applied (the variant in force then), {@code applied} the event that was posted for the use.
     */
    public void run(final TemplateInfo template, final TemplateAppliedEvent applied) {
        final TemplateRef ref = applied.template();
        final List<String> commands = new ArrayList<>(template.actions());
        if (template instanceof LadderTemplate ladder && ref.step().isPresent()
                && ref.step().getAsInt() < ladder.steps().size()) {
            commands.addAll(ladder.steps().get(ref.step().getAsInt()).actions());
        }
        if (commands.isEmpty()) {
            return;
        }
        final Map<String, String> values = new HashMap<>(applied.punishment()
                .map(punishment -> ConsoleActions.values(punishment, this.names::name))
                .orElseGet(() -> ConsoleActions.values(applied.victim(),
                        applied.actor().operator(), applied.reason(), this.names::name)));
        values.put("template", ref.name());
        values.put("step", ref.step().isPresent()
                ? Integer.toString(ref.step().getAsInt() + 1) : "");
        ConsoleActions.run(this.platform, commands, values, this.logger,
                "template " + ref.name());
    }

}
