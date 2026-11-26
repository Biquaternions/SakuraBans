package net.serlith.sakurabans.core.importer;

import com.google.common.base.Preconditions;
import java.util.List;
import java.util.Map;
import net.serlith.sakurabans.api.validation.FieldError;
import org.jspecify.annotations.Nullable;

public record ImportReport(boolean dryRun, long imported, long skipped, List<Failure> failures,
        long failed, long players, long superseded, long truncated, Map<String, Long> tables) {

    public ImportReport {
        failures = List.copyOf(failures);
        tables = Map.copyOf(tables);
        Preconditions.checkArgument(failed >= failures.size(), "fewer failures than kept");
    }

    public record Failure(long index, @Nullable String externalId, List<FieldError> errors) {

        public Failure {
            errors = List.copyOf(errors);
            Preconditions.checkArgument(!errors.isEmpty(), "a failure has errors");
        }
    }

}
