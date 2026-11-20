package net.serlith.sakurabans.core.sync;

public sealed interface SyncPacket {

    record PunishmentEnforcePacket(long punishmentId) implements SyncPacket {
    }

    record PunishmentRevokePacket(long punishmentId) implements SyncPacket {
    }

}
