package net.serlith.sakurabans.api.template;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.serlith.sakurabans.api.model.PunishmentType;
import net.serlith.sakurabans.api.model.Scope;

/**
 * A template, as currently stored. Templates live in the database and are shared by every
 * instance; {@code templates.yml} seeds them. Implemented by SakuraBans; don't implement it.
 */
public interface TemplateInfo {

    /**
     * The template's name.
     *
     * @return the name, lower case
     */
    String name();

    /**
     * The type of the punishments it creates.
     *
     * @return the type
     */
    PunishmentType type();

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
     * @return {@code true} for shadow mutes; always {@code false} unless the type is a mute
     */
    boolean shadow();

    /**
     * The scope its punishments get when the punishment names none.
     *
     * @return the scope, or empty for the configured default scope
     */
    Optional<Scope> scope();

    /**
     * The reason punishments from this template get when none is given.
     *
     * @return the reason, plain text
     */
    String reason();

    /**
     * The ladder: the first punishment uses step 0, the next one step 1, and so on; past the
     * end, the last step repeats.
     *
     * @return the steps, never empty
     */
    List<TemplateStep> ladder();

    /**
     * How long a punishment counts towards the ladder (LiteBans' {@code expire_ladder}): older
     * ones are left out when the step is counted.
     *
     * @return the window, or empty if every earlier punishment counts
     */
    Optional<Duration> ladderExpiry();

    /**
     * Whether progress is shared through addresses (LiteBans' {@code ip_template}): earlier
     * punishments of the victim's alts and addresses count too.
     *
     * @return {@code true} if shared
     */
    boolean addressShared();

    /**
     * Whether using the template needs the permission {@code sakurabans.template.<name>}.
     *
     * @return {@code true} if it does
     */
    boolean permissionRequired();

    /**
     * Console commands run after every punishment from this template, before the step's own
     * {@link TemplateStep#actions()}; same placeholders.
     *
     * @return the commands, possibly empty
     */
    List<String> actions();

    /**
     * The groups the template is in, with its weight in each. Templates in a group share their
     * progress: an earlier punishment from a group mate counts as its weight in that group.
     *
     * @return group name to weight (1-100), possibly empty
     */
    Map<String, Integer> groups();
}
