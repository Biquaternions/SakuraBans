package net.serlith.sakurabans.core.storage.repository;

import com.google.common.base.Preconditions;
import java.time.Instant;
import java.util.Map;
import java.util.function.LongToIntFunction;
import org.jspecify.annotations.Nullable;

/**
 * How a ladder counts a victim's step: the uses of the templates in {@code weights} on the
 * victim (with its alts and addresses when {@code addressShared}) since {@code since} (null: all),
 * leaving out uses whose punishment was voided, each worth its template's weight; {@code stepFor}
 * turns the points into the step. The same count runs before an apply and again, under the
 * victim's lock, in the transaction that stores the use.
 */
public record LadderCount(Map<String, Integer> weights, boolean addressShared,
        @Nullable Instant since, Instant now, LongToIntFunction stepFor) {

    public LadderCount {
        weights = Map.copyOf(weights);
        Preconditions.checkNotNull(now, "now");
        Preconditions.checkNotNull(stepFor, "stepFor");
    }
}
