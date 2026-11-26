package net.serlith.sakurabans.core.sync;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.serlith.sakurabans.api.model.Visibility;
import net.serlith.sakurabans.core.model.SubjectType;
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

    record PunishmentRevokePacket(long punishmentId, Visibility visibility)
            implements SyncPacket {
    }

    record PunishmentEditedPacket(long punishmentId, boolean rescoped) implements SyncPacket {
    }

    record PunishmentsEndedPacket(List<Long> punishmentIds) implements SyncPacket {

        public PunishmentsEndedPacket {
            punishmentIds = List.copyOf(punishmentIds);
        }
    }

    record PunishmentExpungedPacket(long punishmentId, boolean range) implements SyncPacket {
    }

    record PlayerDataErasedPacket(UUID uuid) implements SyncPacket {
    }

    record AllowListChangedPacket() implements SyncPacket {
    }

    record LockdownChangedPacket() implements SyncPacket {
    }

    record EntityChangedPacket(SubjectType entity) implements SyncPacket {
    }

    record ImportedPacket() implements SyncPacket {
    }

}
