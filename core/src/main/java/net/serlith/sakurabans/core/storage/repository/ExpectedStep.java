package net.serlith.sakurabans.core.storage.repository;

import com.google.common.base.Preconditions;

/**
 * The step a ladder use was counted at (S64). The transaction that stores the use locks the
 * victim's {@code sb_victims} row, runs {@code count} again and stores nothing when it gives
 * another step: a concurrent apply on the same victim was stored in between.
 */
public record ExpectedStep(LadderCount count, int step) {

    public ExpectedStep {
        Preconditions.checkNotNull(count, "count");
        Preconditions.checkArgument(step >= 0, "step must not be negative");
    }
}
