package net.serlith.sakurabans.api.validation;

import java.util.Objects;

/**
 * One problem with one value, as reported by {@code validate()} or a
 * {@link ValidationException}.
 */
public interface FieldError {

    /**
     * Which value is wrong, as a dot-separated path of builder properties or factory parameters,
     * for example {@code reason} or {@code applicableTo.uuid}.
     *
     * @return the path
     */
    String path();

    /**
     * What is wrong, as a stable code that a UI can translate.
     *
     * @return the code
     */
    Code code();

    /**
     * What is wrong, in English, for logs and developers.
     *
     * @return the message
     */
    String message();

    /**
     * Creates a field error.
     *
     * @param path the path of the value
     * @param code what is wrong
     * @param message a description in English
     * @return the error
     */
    static FieldError of(final String path, final Code code, final String message) {
        record Impl(String path, Code code, String message) implements FieldError {
            Impl {
                Objects.requireNonNull(path, "path");
                Objects.requireNonNull(code, "code");
                Objects.requireNonNull(message, "message");
            }
        }

        return new Impl(path, code, message);
    }

    /**
     * Stable error codes. New codes are added in minor versions; treat unknown ones as
     * {@link #INVALID}.
     */
    enum Code {

        /** The text is blank. */
        BLANK("blank"),

        /** The text is longer than allowed. */
        TOO_LONG("too_long"),

        /** The name isn't 1-64 characters of {@code a-z}, {@code 0-9}, {@code _} or {@code -}. */
        INVALID_IDENTIFIER("invalid_identifier"),

        /** The text doesn't have the expected format (an evidence reference, an address, ...). */
        INVALID_FORMAT("invalid_format"),

        /** The number or duration must be positive. */
        NOT_POSITIVE("not_positive"),

        /** The number is outside its allowed range. */
        OUT_OF_RANGE("out_of_range"),

        /** The value can't be combined with another value that is set. */
        EXCLUSIVE("exclusive"),

        /** The value isn't allowed in this combination (a duration on a kick, ...). */
        NOT_ALLOWED("not_allowed"),

        /** A start is not before its end. */
        WRONG_ORDER("wrong_order"),

        /** Something else is wrong; see the message. */
        INVALID("invalid");

        private final String id;

        Code(final String id) {
            this.id = id;
        }

        /**
         * The code as stable lower-case text, for JSON and translation keys.
         *
         * @return the text, for example {@code too_long}
         */
        public String id() {
            return this.id;
        }
    }
}
