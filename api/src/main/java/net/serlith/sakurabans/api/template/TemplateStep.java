package net.serlith.sakurabans.api.template;

import java.time.Duration;
import java.util.Optional;
import net.serlith.sakurabans.api.validation.FieldError.Code;
import net.serlith.sakurabans.api.validation.Validator;
import org.jspecify.annotations.Nullable;

/**
 * One step of a template's ladder.
 */
public interface TemplateStep {

    /**
     * How long a punishment at this step lasts.
     *
     * @return the duration, or empty for permanent
     */
    Optional<Duration> duration();

    /**
     * Creates a step.
     *
     * @param duration how long it lasts, positive, or {@code null} for permanent
     * @return the step
     * @throws net.serlith.sakurabans.api.validation.ValidationException if the duration isn't
     *         positive
     */
    static TemplateStep of(final @Nullable Duration duration) {
        record Impl(Optional<Duration> duration) implements TemplateStep {
        }

        Validator.create().check(duration == null || duration.isPositive(), "duration",
                Code.NOT_POSITIVE, "must be positive").throwIfAny();
        return new Impl(Optional.ofNullable(duration));
    }
}
