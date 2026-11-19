package net.serlith.sakurabans.api.validation;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import net.serlith.sakurabans.api.validation.FieldError.Code;
import org.jspecify.annotations.Nullable;

/**
 * Collects {@link FieldError}s. The API's builders use it for their {@code validate()} methods,
 * and you can use it to report problems in the same shape. Get a new, empty one with
 * {@link #create()}; an instance is not thread-safe.
 */
public interface Validator {

    /** The longest identifier (template, kind, server and category names). */
    int MAX_IDENTIFIER = 64;

    /**
     * Records an error.
     *
     * @param path the path of the value
     * @param code what is wrong
     * @param message a description in English
     * @return this validator
     */
    Validator add(String path, Code code, String message);

    /**
     * The errors recorded so far.
     *
     * @return the errors, in the order they were recorded
     */
    List<FieldError> errors();

    /**
     * Records an error unless a condition holds.
     *
     * @param valid the condition that must hold
     * @param path the path of the value
     * @param code what is wrong otherwise
     * @param message a description in English
     * @return this validator
     */
    default Validator check(
            final boolean valid, final String path, final Code code, final String message) {
        return valid ? this : this.add(path, code, message);
    }

    /**
     * Checks that a text, if present, is at most {@code maxLength} characters long.
     *
     * @param path the path of the value
     * @param value the text, or {@code null}
     * @param maxLength the maximum length
     * @return this validator
     */
    default Validator maxLength(
            final String path, final @Nullable String value, final int maxLength) {
        return this.check(value == null || value.length() <= maxLength, path, Code.TOO_LONG,
                "is longer than " + maxLength + " characters");
    }

    /**
     * Checks that a text, if present, is not blank and at most {@code maxLength} characters long.
     *
     * @param path the path of the value
     * @param value the text, or {@code null}
     * @param maxLength the maximum length
     * @return this validator
     */
    default Validator name(final String path, final @Nullable String value, final int maxLength) {
        this.check(value == null || !value.isBlank(), path, Code.BLANK, "must not be blank");
        return this.maxLength(path, value, maxLength);
    }

    /**
     * Checks that a text, if present, is an identifier in any case: 1-{@value #MAX_IDENTIFIER}
     * characters of {@code a-z}, {@code 0-9}, {@code _} or {@code -}.
     *
     * @param path the path of the value
     * @param value the text, or {@code null}
     * @return this validator
     */
    default Validator identifier(final String path, final @Nullable String value) {
        return this.check(value == null || normalizeIdentifier(value).isPresent(), path,
                Code.INVALID_IDENTIFIER, "must be 1-" + MAX_IDENTIFIER
                        + " characters of a-z, 0-9, '_' or '-': '" + value + "'");
    }

    /**
     * Checks that a text, if present, is an evidence type: {@code <namespace>:<type>}, both
     * parts of {@code a-z}, {@code 0-9}, {@code _} or {@code -} (for example
     * {@code hydra:chat-snapshot}).
     *
     * @param path the path of the value
     * @param value the text, or {@code null}
     * @return this validator
     */
    default Validator evidenceType(final String path, final @Nullable String value) {
        return this.check(value == null || Patterns.EVIDENCE_TYPE.matcher(value).matches(), path,
                Code.INVALID_FORMAT, "must be <namespace>:<type>: '" + value + "'");
    }

    /**
     * Throws if any error was recorded.
     *
     * @throws ValidationException with every recorded error
     */
    default void throwIfAny() {
        ValidationException.throwIfAny(this.errors());
    }

    /**
     * Creates a new, empty validator.
     *
     * @return the validator
     */
    static Validator create() {
        final class Impl implements Validator {

            private final List<FieldError> errors = new ArrayList<>();

            @Override
            public Validator add(final String path, final Code code, final String message) {
                this.errors.add(FieldError.of(path, code, message));
                return this;
            }

            @Override
            public List<FieldError> errors() {
                return List.copyOf(this.errors);
            }
        }

        return new Impl();
    }

    /**
     * Lower-cases an identifier and checks it.
     *
     * @param value the text, in any case
     * @return the lower-case identifier, or empty if it isn't one
     */
    static Optional<String> normalizeIdentifier(final String value) {
        final String lower = value.toLowerCase(Locale.ROOT);
        return lower.length() <= MAX_IDENTIFIER && Patterns.IDENTIFIER.matcher(lower).matches()
                ? Optional.of(lower)
                : Optional.empty();
    }
}
