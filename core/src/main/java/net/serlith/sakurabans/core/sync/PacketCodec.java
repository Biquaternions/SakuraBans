package net.serlith.sakurabans.core.sync;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import net.kyori.adventure.text.logger.slf4j.ComponentLogger;
import net.serlith.sakurabans.api.model.Visibility;

public final class PacketCodec {

    static final int VERSION = 1;
    private static final int ENFORCE = 0;
    private static final int REVOKE = 1;
    private static final int ALLOW_LIST = 2;

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
                case SyncPacket.AllowListChangedPacket allowList -> out.writeByte(ALLOW_LIST);
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
                        Visibility.valueOf(in.readUTF().toUpperCase(Locale.ROOT))));
                case ALLOW_LIST -> Optional.of(new SyncPacket.AllowListChangedPacket());
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
