package net.serlith.sakurabans.api.template;

import java.util.List;
import net.serlith.sakurabans.api.model.PunishmentType;

/**
 * A template from {@code templates.yml}, as currently loaded. Implemented by SakuraBans; don't
 * implement it.
 */
public interface TemplateInfo {

    /**
     * The template's name.
     *
     * @return the name, lower case
     */
    String name();

    /**
     * The kind of punishment it creates.
     *
     * @return the type
     */
    PunishmentType type();

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
}
