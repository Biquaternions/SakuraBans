package net.serlith.sakurabans.core.model;

import com.google.common.base.Preconditions;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.serlith.sakurabans.api.imports.ImportReport;
import net.serlith.sakurabans.api.validation.FieldError;
import org.jspecify.annotations.Nullable;

public record SakuraImportReport(boolean dryRun, long imported, long skipped,
        List<ImportReport.Failure> failures, long failed, long players, long superseded,
        long truncated, Map<String, Long> tables) implements ImportReport {

    public SakuraImportReport {
        failures = List.copyOf(failures);
        tables = Map.copyOf(tables);
        Preconditions.checkArgument(failed >= failures.size(), "fewer failures than kept");
    }

    public record Failure(long index, @Nullable String externalIdOrNull,
            List<FieldError> errors) implements ImportReport.Failure {

        public Failure {
            errors = List.copyOf(errors);
            Preconditions.checkArgument(!errors.isEmpty(), "a failure has errors");
        }

        @Override
        public Optional<String> externalId() {
            return Optional.ofNullable(this.externalIdOrNull);
        }
    }

}
