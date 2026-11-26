package net.serlith.sakurabans.core.sync;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.kyori.adventure.text.logger.slf4j.ComponentLogger;
import net.serlith.sakurabans.api.model.Visibility;
import net.serlith.sakurabans.core.model.SubjectType;

public final class PacketCodec {

    static final int VERSION = 1;
    private static final int ENFORCE = 0;
    private static final int REVOKE = 1;
    private static final int EDITED = 2;
    private static final int ENDED = 3;
    private static final int EXPUNGED = 4;
    private static final int ERASED = 5;
    private static final int ALLOW_LIST = 6;
    private static final int LOCKDOWN = 7;
    private static final int ENTITY_CHANGED = 8;
    private static final int IMPORTED = 9;

    private final UUID instanceId;
    private final ComponentLogger logger;

    public PacketCodec(final UUID instanceId, final ComponentLogger logger) {
        this.instanceId = instanceId;
        this.logger = logger;
    }

    public byte[] encode(final SyncPacket packet) {
        final ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (DataOutputStream out = new DataOutputStream(bytes)) {
            out.writeByte(VERSION);
            out.writeLong(this.instanceId.getMostSignificantBits());
            out.writeLong(this.instanceId.getLeastSignificantBits());
            switch (packet) {
                case SyncPacket.PunishmentEnforcePacket enforce -> {
                    out.writeByte(ENFORCE);
                    out.writeLong(enforce.punishmentId());
                    out.writeBoolean(enforce.supersededIdOrNull() != null);
                    if (enforce.supersededIdOrNull() != null) {
                        out.writeLong(enforce.supersededIdOrNull());
                    }
                }
                case SyncPacket.PunishmentRevokePacket revoke -> {
                    out.writeByte(REVOKE);
                    out.writeLong(revoke.punishmentId());
                    out.writeUTF(revoke.visibility().name());
                }
                case SyncPacket.PunishmentEditedPacket edited -> {
                    out.writeByte(EDITED);
                    out.writeLong(edited.punishmentId());
                    out.writeBoolean(edited.rescoped());
                }
                case SyncPacket.PunishmentsEndedPacket ended -> {
                    out.writeByte(ENDED);
                    out.writeInt(ended.punishmentIds().size());
                    for (final long id : ended.punishmentIds()) {
                        out.writeLong(id);
                    }
                }
                case SyncPacket.PunishmentExpungedPacket expunged -> {
                    out.writeByte(EXPUNGED);
                    out.writeLong(expunged.punishmentId());
                    out.writeBoolean(expunged.range());
                }
                case SyncPacket.PlayerDataErasedPacket erased -> {
                    out.writeByte(ERASED);
                    out.writeLong(erased.uuid().getMostSignificantBits());
                    out.writeLong(erased.uuid().getLeastSignificantBits());
                }
                case SyncPacket.AllowListChangedPacket allowList -> out.writeByte(ALLOW_LIST);
                case SyncPacket.LockdownChangedPacket lockdown -> out.writeByte(LOCKDOWN);
                case SyncPacket.EntityChangedPacket changed -> {
                    out.writeByte(ENTITY_CHANGED);
                    out.writeUTF(changed.entity().name());
                }
                case SyncPacket.ImportedPacket imported -> out.writeByte(IMPORTED);
            }
        } catch (final IOException e) {
            throw new UncheckedIOException(e);
        }
        return bytes.toByteArray();
    }

    public Optional<SyncPacket> decode(final byte[] data) {
        try (DataInputStream in = new DataInputStream(new ByteArrayInputStream(data))) {
            final int version = in.readUnsignedByte();
            if (version != VERSION) {
                this.logger.warn("Ignoring a sync message of version {} (this instance reads {});"
                        + " do all instances run the same SakuraBans version?", version, VERSION);
                return Optional.empty();
            }
            if (new UUID(in.readLong(), in.readLong()).equals(this.instanceId)) {
                return Optional.empty();
            }
            return switch (in.readUnsignedByte()) {
                case ENFORCE -> {
                    final long id = in.readLong();
                    yield Optional.of(new SyncPacket.PunishmentEnforcePacket(id,
                            in.readBoolean() ? in.readLong() : null));
                }
                case REVOKE -> Optional.of(new SyncPacket.PunishmentRevokePacket(in.readLong(),
                        Visibility.valueOf(in.readUTF())));
                case EDITED -> Optional.of(new SyncPacket.PunishmentEditedPacket(in.readLong(),
                        in.readBoolean()));
                case ENDED -> {
                    final int count = in.readInt();
                    final List<Long> ids = new ArrayList<>(count);
                    for (int i = 0; i < count; i++) {
                        ids.add(in.readLong());
                    }
                    yield Optional.of(new SyncPacket.PunishmentsEndedPacket(ids));
                }
                case EXPUNGED -> Optional.of(new SyncPacket.PunishmentExpungedPacket(
                        in.readLong(), in.readBoolean()));
                case ERASED -> Optional.of(new SyncPacket.PlayerDataErasedPacket(
                        new UUID(in.readLong(), in.readLong())));
                case ALLOW_LIST -> Optional.of(new SyncPacket.AllowListChangedPacket());
                case LOCKDOWN -> Optional.of(new SyncPacket.LockdownChangedPacket());
                case ENTITY_CHANGED -> Optional.of(new SyncPacket.EntityChangedPacket(
                        SubjectType.valueOf(in.readUTF())));
                case IMPORTED -> Optional.of(new SyncPacket.ImportedPacket());
                default -> {
                    this.logger.warn("Ignoring a sync message of an unknown type");
                    yield Optional.empty();
                }
            };
        } catch (final IOException | IllegalArgumentException e) {
            this.logger.warn("Ignoring a sync message that can't be read", e);
            return Optional.empty();
        }
    }

}
