package net.serlith.sakurabans.core.sync;

import java.util.Optional;
import net.serlith.sakurabans.api.model.Visibility;
import org.jspecify.annotations.Nullable;

public sealed interface SyncPacket {

    record PunishmentEnforcePacket(long punishmentId, @Nullable Long supersededIdOrNull)
            implements SyncPacket {

        public static PunishmentEnforcePacket of(final long punishmentId) {
            return new PunishmentEnforcePacket(punishmentId, null);
        }

        public Optional<Long> supersededId() {
            return Optional.ofNullable(this.supersededIdOrNull);
        }
    }

    record AllowListChangedPacket() implements SyncPacket {
    }

    record PunishmentRevokePacket(long punishmentId, Visibility visibility)
            implements SyncPacket {
    }

}
