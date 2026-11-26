package net.serlith.sakurabans.core.model;

import com.google.common.base.Preconditions;
import java.util.List;
import net.serlith.sakurabans.api.validation.FieldError;

public sealed interface AdminResult<T> {

    record Ok<T>(T value) implements AdminResult<T> {

        public Ok {
            Preconditions.checkNotNull(value, "value");
        }
    }

    record ValidationFailed<T>(List<FieldError> errors) implements AdminResult<T> {

        public ValidationFailed {
            errors = List.copyOf(errors);
            Preconditions.checkArgument(!errors.isEmpty(), "a failed validation has errors");
        }
    }

    record Conflict<T>(long currentVersion) implements AdminResult<T> {
    }

    record NotFound<T>() implements AdminResult<T> {
    }

    record Forbidden<T>() implements AdminResult<T> {
    }

    static <T> AdminResult<T> invalid(final String path, final FieldError.Code code,
            final String message) {
        return new ValidationFailed<>(List.of(FieldError.of(path, code, message)));
    }
}
