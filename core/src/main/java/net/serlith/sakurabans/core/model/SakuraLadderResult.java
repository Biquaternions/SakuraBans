package net.serlith.sakurabans.core.model;

import com.google.common.base.Preconditions;
import java.util.Optional;
import net.serlith.sakurabans.api.model.Punishment;
import net.serlith.sakurabans.api.punish.LadderResult;
import net.serlith.sakurabans.api.punish.PunishResult;
import org.jspecify.annotations.Nullable;

public final class SakuraLadderResult {

    private SakuraLadderResult() {
    }

    public record Punished(Punishment punishment, @Nullable Punishment supersededOrNull,
            int step) implements LadderResult.Punished {

        public Punished {
            Preconditions.checkNotNull(punishment, "punishment");
            Preconditions.checkArgument(step >= 0, "step must not be negative: %s", step);
        }

        @Override
        public Optional<Punishment> superseded() {
            return Optional.ofNullable(this.supersededOrNull);
        }
    }

    public record ActionsOnly(int step) implements LadderResult.ActionsOnly {

        public ActionsOnly {
            Preconditions.checkArgument(step >= 0, "step must not be negative: %s", step);
        }
    }

    public record Failure(PunishResult.Failure.Reason reason,
            @Nullable Punishment conflictingOrNull) implements LadderResult.Failure {

        public Failure {
            Preconditions.checkNotNull(reason, "reason");
        }

        public static Failure of(final PunishResult.Failure failure) {
            return new SakuraLadderResult.Failure(failure.reason(),
                    failure.conflicting().orElse(null));
        }

        @Override
        public Optional<Punishment> conflicting() {
            return Optional.ofNullable(this.conflictingOrNull);
        }
    }
}
