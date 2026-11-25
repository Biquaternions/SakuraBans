package net.serlith.sakurabans.core.model;

import com.google.common.base.Preconditions;
import java.util.List;
import net.serlith.sakurabans.api.model.Punishment;
import net.serlith.sakurabans.api.punish.RollbackResult;

public final class SakuraRollbackResult {

    private SakuraRollbackResult() {
    }

    public record Success(List<Punishment> rolledBack) implements RollbackResult.Success {

        public Success {
            rolledBack = List.copyOf(rolledBack);
        }
    }

    public record Failure(Reason reason) implements RollbackResult.Failure {

        public Failure {
            Preconditions.checkNotNull(reason, "reason");
        }
    }
}
