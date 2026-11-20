package net.serlith.sakurabans.core.model;

import com.google.common.base.Preconditions;
import java.util.List;
import net.serlith.sakurabans.api.model.Punishment;
import net.serlith.sakurabans.api.punish.RevokeResult;

public final class SakuraRevokeResult {

    private SakuraRevokeResult() {
    }

    public record Success(List<Punishment> revoked) implements RevokeResult.Success {

        public Success {
            revoked = List.copyOf(revoked);
            Preconditions.checkArgument(!revoked.isEmpty(), "nothing revoked");
        }
    }

    public record Failure(Reason reason) implements RevokeResult.Failure {

        public Failure {
            Preconditions.checkNotNull(reason, "reason");
        }
    }

}
