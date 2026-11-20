package net.serlith.sakurabans.core.model;

import com.google.common.base.Preconditions;
import java.util.Optional;
import net.serlith.sakurabans.api.model.Punishment;
import net.serlith.sakurabans.api.punish.PunishResult;
import org.jspecify.annotations.Nullable;

public final class SakuraPunishResult {

    private SakuraPunishResult() {
    }

    public record Success(Punishment punishment, @Nullable Punishment supersededOrNull)
            implements PunishResult.Success {

        public Success {
            Preconditions.checkNotNull(punishment, "punishment");
        }

        @Override
        public Optional<Punishment> superseded() {
            return Optional.ofNullable(this.supersededOrNull);
        }
    }

    public record Failure(Reason reason, @Nullable Punishment conflictingOrNull)
            implements PunishResult.Failure {

        public Failure {
            Preconditions.checkNotNull(reason, "reason");
            Preconditions.checkArgument(
                    (reason == Reason.ALREADY_PUNISHED) == (conflictingOrNull != null),
                    "only ALREADY_PUNISHED has a conflicting punishment");
        }

        public Failure(final Reason reason) {
            this(reason, null);
        }

        @Override
        public Optional<Punishment> conflicting() {
            return Optional.ofNullable(this.conflictingOrNull);
        }
    }

}
