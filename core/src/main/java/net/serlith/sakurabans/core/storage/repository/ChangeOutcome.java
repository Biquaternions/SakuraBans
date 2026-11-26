package net.serlith.sakurabans.core.storage.repository;

import com.google.common.base.Preconditions;
import net.serlith.sakurabans.api.model.Punishment;

public sealed interface ChangeOutcome {

    record NotFound() implements ChangeOutcome {
    }

    record Refused(Problem problem) implements ChangeOutcome {

        public Refused {
            Preconditions.checkNotNull(problem, "problem");
        }
    }

    record Changed(Punishment previous, Punishment current) implements ChangeOutcome {

        public Changed {
            Preconditions.checkNotNull(previous, "previous");
            Preconditions.checkNotNull(current, "current");
        }
    }

    enum Problem {
        NOT_ACTIVE,
        INVALID_EXPIRY,
        ALREADY_PUNISHED,
        ALREADY_VOIDED,
        EVIDENCE_ALREADY_ATTACHED,
        EVIDENCE_NOT_ATTACHED
    }

}
