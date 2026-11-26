package net.serlith.sakurabans.core.storage.repository;

import com.google.common.base.Preconditions;
import java.time.Instant;
import net.serlith.sakurabans.api.model.EndKind;
import net.serlith.sakurabans.api.model.Operator;
import org.jspecify.annotations.Nullable;

public record ImportRow(NewPunishment punishment, @Nullable EndKind endKind,
        @Nullable Instant endedAt, @Nullable Operator endedBy, @Nullable String endReason) {

    public ImportRow {
        Preconditions.checkNotNull(punishment, "punishment");
        Preconditions.checkArgument((endKind == null) == (endedAt == null),
                "an end has a kind and an instant");
    }
}
