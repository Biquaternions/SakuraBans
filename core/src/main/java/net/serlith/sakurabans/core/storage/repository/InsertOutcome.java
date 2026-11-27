package net.serlith.sakurabans.core.storage.repository;

import net.serlith.sakurabans.api.model.Punishment;
import org.jspecify.annotations.Nullable;

public sealed interface InsertOutcome {

    record Inserted(Punishment punishment, @Nullable Punishment superseded)
            implements InsertOutcome {
    }

    record Rejected(Punishment existing) implements InsertOutcome {
    }

    /**
     * Nothing stored: the ladder step the use expected (an {@link ExpectedStep}) is no longer
     * the victim's step, which is now {@code step}.
     */
    record StepChanged(int step) implements InsertOutcome {
    }

}
