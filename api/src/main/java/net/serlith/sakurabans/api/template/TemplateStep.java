package net.serlith.sakurabans.api.template;

import java.time.Duration;
import java.util.Optional;

/**
 * One step of a template's ladder. Implemented by SakuraBans; don't implement it.
 */
public interface TemplateStep {

    /**
     * How long a punishment at this step lasts.
     *
     * @return the duration, or empty for permanent
     */
    Optional<Duration> duration();
}
