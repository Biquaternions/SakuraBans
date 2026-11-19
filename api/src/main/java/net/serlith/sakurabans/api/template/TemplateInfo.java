package net.serlith.sakurabans.api.template;

import java.util.List;
import java.util.Set;
import net.serlith.sakurabans.api.admin.Versioned;
import net.serlith.sakurabans.api.model.PunishmentType;

/**
 * A template, as currently stored. Templates live in the database and are shared by every
 * instance; {@code templates.yml} seeds them. Implemented by SakuraBans; don't implement it.
 */
public interface TemplateInfo extends Versioned {

    /**
     * The template's name.
     *
     * @return the name, lower case
     */
    String name();

    /**
     * The punishment kind it creates.
     *
     * @return the kind id
     */
    String kind();

    /**
     * The base type of its kind.
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

    /**
     * Evidence types punishments from this template must have.
     *
     * @return the types, possibly empty
     */
    Set<String> requiredEvidence();

    /**
     * Evidence types captured automatically when a punishment from this template is created,
     * through the registered {@link net.serlith.sakurabans.api.integration.EvidenceCapturer}s.
     *
     * @return the types, possibly empty
     */
    Set<String> captureEvidence();
}
