package net.serlith.sakurabans.api.template;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.serlith.sakurabans.api.model.Scope;

/**
 * A template, as currently stored. Templates live in the database and are shared by every
 * instance; {@code templates.yml} seeds them. A template is either a {@link PresetTemplate} (one
 * punishment type, used by that type's commands) or a {@link LadderTemplate} (escalating steps,
 * applied with {@code /punish}). Names are unique across both. A template may have
 * <em>variants</em>: whole definitions of the same kind for a server or category, in force
 * there instead of the global definition (see {@link TemplateService#template(String, Scope)}).
 * Implemented by SakuraBans; don't implement it.
 */
public sealed interface TemplateInfo permits PresetTemplate, LadderTemplate {

    /**
     * The template's name.
     *
     * @return the name, lower case
     */
    String name();

    /**
     * Where this definition is in force: empty for the global definition, else the server or
     * category of this variant. Not to be confused with {@link #scope()}, where its punishments
     * apply. A variant always has its global definition's kind and groups.
     *
     * @return the variant's scope, or empty for the global definition
     */
    Optional<Scope> variant();

    /**
     * Whether its punishments target the player's current address instead of the player, as an
     * IP ban or IP mute does.
     *
     * @return {@code true} if they target the address
     */
    boolean ip();

    /**
     * Whether its mutes are shadow mutes: the muted player's messages are shown to themselves
     * only.
     *
     * @return {@code true} for shadow mutes; only on a mute preset or a ladder with a mute step
     */
    boolean shadow();

    /**
     * What a player punished from this template is shown instead of the network's message for
     * the type (the ban and kick screens, the mute notice, the warning): MiniMessage, with the
     * placeholders of the network's message. A ladder step's {@link TemplateStep#message()}
     * wins.
     *
     * @return the message, or empty for the network's
     */
    Optional<String> message();

    /**
     * How the mute notice and the warning reach the punished player while online: chat or a
     * dialog. Only a mute or warn preset, or a ladder with a mute or warn step, may show a
     * dialog; a shadow mute preset shows nothing.
     *
     * @return the display, {@link TemplateDisplay#CHAT} unless set
     */
    TemplateDisplay display();

    /**
     * Whether its punishments are silent: staff are told only if they may see silent
     * punishments, and webhooks follow their {@code include-silent} setting, as with the
     * command flag {@code -s}. A command or API call that asks for {@link
     * net.serlith.sakurabans.api.model.Visibility#HIDDEN} or {@link
     * net.serlith.sakurabans.api.model.Visibility#QUIET} gets that instead; one that leaves the
     * visibility at {@link net.serlith.sakurabans.api.model.Visibility#NORMAL} gets
     * {@link net.serlith.sakurabans.api.model.Visibility#SILENT}. Revoking such a punishment is
     * not affected.
     *
     * @return {@code true} if its punishments are silent; {@code false} unless set
     */
    boolean silent();

    /**
     * The scope its punishments get when the command names none.
     *
     * @return the scope, or empty for the configured default scope
     */
    Optional<Scope> scope();

    /**
     * The reason punishments from this template get when none is given. A ladder step's
     * {@link TemplateStep#reason()} wins.
     *
     * @return the reason, plain text
     */
    String reason();

    /**
     * Whether using the template needs the permission {@code sakurabans.template.<name>}.
     *
     * @return {@code true} if it does
     */
    boolean permissionRequired();

    /**
     * Console commands run every time the template is applied, before a ladder step's own
     * {@link TemplateStep#actions()}. They may use the placeholders {@code {victim}},
     * {@code {uuid}}, {@code {address}}, {@code {id}}, {@code {operator}}, {@code {reason}},
     * {@code {template}}, {@code {step}} (counted from 1, empty for a preset) and
     * {@code {duration}}.
     *
     * @return the commands, possibly empty
     */
    List<String> actions();

    /**
     * The groups the template is in, with its weight in each. Every use of a template counts
     * towards the ladders of its group mates with its weight in that group, presets included.
     * Groups belong to the global definition: a variant has none of its own, and progress is
     * counted by name, whichever definition was used.
     *
     * @return group name to weight (1-100), possibly empty
     */
    Map<String, Integer> groups();
}
