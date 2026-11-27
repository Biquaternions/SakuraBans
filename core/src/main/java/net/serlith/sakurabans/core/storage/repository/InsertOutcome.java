package net.serlith.sakurabans.core.storage.repository;

import net.serlith.sakurabans.api.model.Punishment;
import org.jspecify.annotations.Nullable;

public sealed interface InsertOutcome {

    record Inserted(Punishment punishment, @Nullable Punishment superseded)
            implements InsertOutcome {
    }

    record Rejected(Punishment existing) implements InsertOutcome {
    }

    record Protected(Punishment existing) implements InsertOutcome {
    }

    record StepChanged(int step) implements InsertOutcome {
    }

}
