package net.serlith.sakurabans.core.model;

import com.google.common.base.Preconditions;
import java.util.List;
import net.serlith.sakurabans.api.admin.AdminResult;
import net.serlith.sakurabans.api.auth.Decision;
import net.serlith.sakurabans.api.validation.FieldError;

public final class SakuraAdminResult {

    private SakuraAdminResult() {
    }

    public record Ok<T>(T value) implements AdminResult.Ok<T> {

        public Ok {
            Preconditions.checkNotNull(value, "value");
        }
    }

    public record ValidationFailed<T>(List<FieldError> errors)
            implements AdminResult.ValidationFailed<T> {

        public ValidationFailed {
            errors = List.copyOf(errors);
            Preconditions.checkArgument(!errors.isEmpty(), "a failed validation has errors");
        }
    }

    public record Conflict<T>(long currentVersion) implements AdminResult.Conflict<T> {
    }

    public record NotFound<T>() implements AdminResult.NotFound<T> {
    }

    public record Forbidden<T>(Decision decision) implements AdminResult.Forbidden<T> {

        public Forbidden {
            Preconditions.checkArgument(!decision.allowed(), "a forbidding decision denies");
        }
    }

    public static <T> AdminResult<T> invalid(final String path, final FieldError.Code code,
            final String message) {
        return new ValidationFailed<>(List.of(FieldError.of(path, code, message)));
    }

}
