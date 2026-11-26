package net.serlith.sakurabans.core.model;

import com.google.common.base.Preconditions;
import net.serlith.sakurabans.api.model.Punishment;
import net.serlith.sakurabans.api.punish.EditResult;
import net.serlith.sakurabans.api.punish.ExpungeResult;
import net.serlith.sakurabans.api.punish.VoidResult;

public final class SakuraChangeResults {

    private SakuraChangeResults() {
    }

    public record EditSuccess(Punishment punishment) implements EditResult.Success {

        public EditSuccess {
            Preconditions.checkNotNull(punishment, "punishment");
        }
    }

    public record EditFailure(EditResult.Failure.Reason reason) implements EditResult.Failure {

        public EditFailure {
            Preconditions.checkNotNull(reason, "reason");
        }
    }

    public record VoidSuccess(Punishment punishment) implements VoidResult.Success {

        public VoidSuccess {
            Preconditions.checkNotNull(punishment, "punishment");
        }
    }

    public record VoidFailure(VoidResult.Failure.Reason reason) implements VoidResult.Failure {

        public VoidFailure {
            Preconditions.checkNotNull(reason, "reason");
        }
    }

    public record ExpungeSuccess(Punishment expunged) implements ExpungeResult.Success {

        public ExpungeSuccess {
            Preconditions.checkNotNull(expunged, "expunged");
        }
    }

    public record ExpungeFailure(ExpungeResult.Failure.Reason reason)
            implements ExpungeResult.Failure {

        public ExpungeFailure {
            Preconditions.checkNotNull(reason, "reason");
        }
    }

}
