package net.serlith.sakurabans.api.template;

import net.serlith.sakurabans.api.model.PunishmentType;

/**
 * A preset: the reason, duration, message and options of one punishment type, used with
 * {@code -t} on that type's commands or as {@code DraftPunishment.template(name)}. A preset has
 * no ladder. Implemented by SakuraBans; don't implement it.
 */
public non-sealed interface PresetTemplate extends TemplateInfo {

    /**
     * The type of its punishments.
     *
     * @return the type
     */
    PunishmentType type();

    /**
     * How long its punishments last. Always open for a kick.
     *
     * @return the duration
     */
    TemplateDuration duration();
}
