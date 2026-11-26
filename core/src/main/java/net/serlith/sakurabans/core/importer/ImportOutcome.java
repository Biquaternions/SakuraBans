package net.serlith.sakurabans.core.importer;

import com.google.common.base.Preconditions;
import java.util.Map;

public sealed interface ImportOutcome {

    record Surveyed(Map<String, Long> rows) implements ImportOutcome {

        public Surveyed {
            rows = Map.copyOf(rows);
        }
    }

    record Done(ImportReport report) implements ImportOutcome {
    }

    record Busy() implements ImportOutcome {
    }

    record Refused(Problem problem, String detail) implements ImportOutcome {

        public Refused {
            Preconditions.checkNotNull(problem, "problem");
            Preconditions.checkNotNull(detail, "detail");
        }
    }

    enum Problem {
        /** {@code detail}: the id. */
        UNKNOWN_SOURCE,
        /** {@code detail}: the {@code import.yml} section without a jdbc-url. */
        NOT_CONFIGURED,
        /** {@code detail}: the errors in {@code import.yml}. */
        CONFIG_INVALID,
        /** {@code detail}: why the database can't be opened. */
        UNREACHABLE,
        /** {@code detail}: why the source can't be read (a missing table or file). */
        UNREADABLE,
        /** Self import: no SakuraBans meta table; {@code detail}: the prefix. */
        NOT_SAKURABANS,
        /** Self import: {@code detail}: the source's schema version. */
        OTHER_VERSION,
        /** Self import: {@code detail}: the tables that already hold rows. */
        TARGET_NOT_EMPTY,
        /** It stopped after storing some batches; {@code detail}: why. */
        FAILED
    }
}
