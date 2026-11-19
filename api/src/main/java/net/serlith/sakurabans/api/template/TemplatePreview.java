package net.serlith.sakurabans.api.template;

import java.time.Duration;
import java.util.Optional;

/**
 * What the next punishment from a template would be for a victim. Implemented by SakuraBans;
 * don't implement it.
 */
public interface TemplatePreview {

    /**
     * The template.
     *
     * @return the template, as currently stored
     */
    TemplateInfo template();

    /**
     * The ladder step it would use, counting the victim's earlier punishments from the template
     * (after {@code expire_ladder}, voided ones excluded).
     *
     * @return the step, counted from 0
     */
    int step();

    /**
     * How long it would last.
     *
     * @return the duration, or empty for permanent
     */
    Optional<Duration> duration();
}
