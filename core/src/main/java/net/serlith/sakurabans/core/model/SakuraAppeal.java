package net.serlith.sakurabans.core.model;

import com.google.common.base.Preconditions;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import net.serlith.sakurabans.api.appeal.Appeal;
import net.serlith.sakurabans.api.appeal.AppealOutcome;
import net.serlith.sakurabans.api.model.Actor;
import org.jspecify.annotations.Nullable;

public record SakuraAppeal(long id, long punishmentId, UUID appellant, String message,
        Instant createdAt, Status status, @Nullable Decision decisionOrNull, long version)
        implements Appeal {

    public SakuraAppeal {
        Preconditions.checkArgument(id >= 1, "id must be positive: %s", id);
        Preconditions.checkNotNull(appellant, "appellant");
        Preconditions.checkNotNull(message, "message");
        Preconditions.checkNotNull(status, "status");
        Preconditions.checkArgument((status == Status.OPEN) == (decisionOrNull == null),
                "only decided appeals have a decision");
    }

    @Override
    public Optional<Decision> decision() {
        return Optional.ofNullable(this.decisionOrNull);
    }

    public record SakuraDecision(AppealOutcome outcome, Actor actor, Instant at,
            @Nullable String noteOrNull) implements Decision {

        @Override
        public Optional<String> note() {
            return Optional.ofNullable(this.noteOrNull);
        }
    }
}
