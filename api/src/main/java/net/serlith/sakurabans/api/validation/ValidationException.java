package net.serlith.sakurabans.api.validation;

import java.io.Serial;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Thrown by {@code build()} and the {@code of} factories when values are invalid. It carries
 * every problem found, not only the first. Only SakuraBans creates it: to raise one yourself,
 * use {@link Validator#throwIfAny()} or {@link #throwIfAny(List)}.
 */
public final class ValidationException extends IllegalArgumentException {

    @Serial
    private static final long serialVersionUID = 1L;

    private final transient List<FieldError> errors;

    /**
     * Creates the exception. Called through {@link #throwIfAny(List)} only.
     *
     * @param errors the problems, not empty
     */
    private ValidationException(final List<FieldError> errors) {
        super(describe(errors));
        this.errors = List.copyOf(errors);
    }

    /**
     * Creates the exception for one problem.
     *
     * @param error the problem
     */
    private ValidationException(final FieldError error) {
        this(List.of(error));
    }

    /**
     * Every problem found.
     *
     * @return the errors, never empty
     */
    public List<FieldError> errors() {
        return this.errors;
    }

    /**
     * Throws if there are errors.
     *
     * @param errors the result of a {@code validate()}
     * @throws ValidationException if {@code errors} isn't empty
     */
    public static void throwIfAny(final List<FieldError> errors) {
        if (!errors.isEmpty()) {
            throw new ValidationException(errors);
        }
    }

    private static String describe(final List<FieldError> errors) {
        if (errors.isEmpty()) {
            throw new IllegalArgumentException("a ValidationException needs at least one error");
        }
        return errors.stream()
                .map(error -> error.path() + ": " + error.message())
                .collect(Collectors.joining("; "));
    }
}
