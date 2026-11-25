package net.serlith.sakurabans.core.sync;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.serlith.sakurabans.api.audit.SubjectType;
import net.serlith.sakurabans.api.event.ChangeType;
import net.serlith.sakurabans.api.model.Actor;
import net.serlith.sakurabans.api.model.Evidence;
import net.serlith.sakurabans.api.model.Operator;
import net.serlith.sakurabans.api.model.Punishment;
import net.serlith.sakurabans.api.model.Visibility;
import net.serlith.sakurabans.api.punish.PunishmentEdit;
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

    record EntityChangedPacket(SubjectType entity, String id, ChangeType change, long version,
            Actor actor) implements SyncPacket {
    }

    record PunishmentRevokePacket(long punishmentId, Visibility visibility)
            implements SyncPacket {
    }

    record PunishmentEditedPacket(long punishmentId, PunishmentEdit previous, PunishmentEdit edit)
            implements SyncPacket {
    }

    record PunishmentVoidedPacket(long punishmentId) implements SyncPacket {
    }

    record PunishmentExpungedPacket(Punishment punishment) implements SyncPacket {
    }

    record LockdownChangedPacket() implements SyncPacket {
    }

    record BroadcastPacket(String text) implements SyncPacket {
    }

    record PunishmentsRolledBackPacket(List<Long> punishmentIds, Operator target, Instant since,
            Actor actor, @Nullable String reasonOrNull, Visibility visibility)
            implements SyncPacket {

        public PunishmentsRolledBackPacket {
            punishmentIds = List.copyOf(punishmentIds);
        }
    }

    record PlayerDataErasedPacket(UUID uuid, long names, long addresses, long expunged,
            long anonymised, long issued, long other, Actor actor) implements SyncPacket {
    }

    record EvidenceChangedPacket(long punishmentId, boolean attached, Evidence evidence)
            implements SyncPacket {
    }

}
