package net.serlith.sakurabans.api.template;

import java.time.Duration;
import java.util.Objects;
import java.util.Optional;
import net.serlith.sakurabans.api.validation.FieldError.Code;
import net.serlith.sakurabans.api.validation.Validator;
import org.jspecify.annotations.Nullable;

/**
 * How long a punishment from a preset or a ladder step lasts.
 *
 * <ul>
 *   <li>{@link Open}: not decided by the template. On a preset the command's duration applies,
 *       else the default for the type (permanent, or {@code warnings.expire-after} for a
 *       warning). On a step the default for the type applies. Kicks and steps without a
 *       punishment are always open.</li>
 *   <li>{@link Permanent}: never expires.</li>
 *   <li>{@link Fixed}: lasts {@link Fixed#length()}.</li>
 * </ul>
 */
public sealed interface TemplateDuration
        permits TemplateDuration.Open, TemplateDuration.Permanent, TemplateDuration.Fixed {

    /**
     * The duration the template leaves open.
     */
    non-sealed interface Open extends TemplateDuration {
    }

    /**
     * A permanent punishment.
     */
    non-sealed interface Permanent extends TemplateDuration {
    }

    /**
     * A punishment of a fixed length.
     */
    non-sealed interface Fixed extends TemplateDuration {

        /**
         * How long it lasts.
         *
         * @return the length, whole seconds, at least one
         */
        Duration length();
    }

    /**
     * The fixed length, if there is one.
     *
     * @return the length, or empty when open or permanent
     */
    default Optional<Duration> fixedLength() {
        return this instanceof Fixed fixed ? Optional.of(fixed.length()) : Optional.empty();
    }

    /**
     * A duration the template leaves open.
     *
     * @return the open duration
     */
    static TemplateDuration open() {
        record Impl() implements Open {
        }

        return new Impl();
    }

    /**
     * A permanent duration.
     *
     * @return the permanent duration
     */
    static TemplateDuration permanent() {
        record Impl() implements Permanent {
        }

        return new Impl();
    }

    /**
     * A fixed duration. Templates are written in whole seconds ({@code templates.yml} and the
     * editor write {@code 90s}, {@code 1h}), so a length with a fraction of a second is refused.
     *
     * @param length how long it lasts, whole seconds, at least one
     * @return the fixed duration
     * @throws net.serlith.sakurabans.api.validation.ValidationException if the length is under
     *         one second or not whole seconds
     */
    static TemplateDuration fixed(final Duration length) {
        record Impl(Duration length) implements Fixed {
        }

        final Validator errors = Validator.create();
        validateLength(errors, "duration", Objects.requireNonNull(length, "length"));
        errors.throwIfAny();
        return new Impl(length);
    }

    /**
     * Checks a length written in a template (a fixed duration, a ladder's expiry): whole
     * seconds, at least one.
     *
     * @param errors where an error is added
     * @param path the error's path
     * @param length the length
     */
    static void validateLength(final Validator errors, final String path,
            final Duration length) {
        Objects.requireNonNull(errors, "errors");
        Objects.requireNonNull(path, "path");
        Objects.requireNonNull(length, "length");
        if (length.compareTo(Duration.ofSeconds(1)) < 0) {
            errors.add(path, Code.OUT_OF_RANGE, "must be at least one second");
        } else if (length.getNano() != 0) {
            errors.add(path, Code.INVALID_FORMAT, "must be whole seconds");
        }
    }

    /**
     * A fixed duration, or permanent for {@code null}.
     *
     * @param length how long it lasts (see {@link #fixed}), or {@code null} for permanent
     * @return the duration
     * @throws net.serlith.sakurabans.api.validation.ValidationException if the length is under
     *         one second or not whole seconds
     */
    static TemplateDuration ofNullable(final @Nullable Duration length) {
        return length == null ? permanent() : fixed(length);
    }
}
