package net.serlith.sakurabans.core.sync;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import net.kyori.adventure.text.logger.slf4j.ComponentLogger;
import net.serlith.sakurabans.api.audit.SubjectType;
import net.serlith.sakurabans.api.event.ChangeType;
import net.serlith.sakurabans.api.model.Actor;
import net.serlith.sakurabans.api.model.Operator;
import net.serlith.sakurabans.api.model.Visibility;
import net.serlith.sakurabans.core.model.SakuraPlayerData;
import net.serlith.sakurabans.core.model.ValueCodec;

public final class PacketCodec {

    static final int VERSION = 1;
    private static final int ENFORCE = 0;
    private static final int REVOKE = 1;
    private static final int ALLOW_LIST = 2;
    private static final int ENTITY_CHANGED = 3;
    private static final int EDITED = 4;
    private static final int VOIDED = 5;
    private static final int EXPUNGED = 6;
    private static final int EVIDENCE = 7;
    private static final int LOCKDOWN = 8;
    private static final int IMPORTED = 9;
    private static final int ROLLED_BACK = 10;
    private static final int ERASED = 11;

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
                case SyncPacket.EntityChangedPacket changed -> {
                    out.writeByte(ENTITY_CHANGED);
                    out.writeUTF(changed.entity().name());
                    out.writeUTF(changed.id());
                    out.writeUTF(changed.change().name());
                    out.writeLong(changed.version());
                    ValueCodec.writeActor(out, changed.actor());
                }
                case SyncPacket.PunishmentEditedPacket edited -> {
                    out.writeByte(EDITED);
                    out.writeLong(edited.punishmentId());
                    ValueCodec.writeEdit(out, edited.previous());
                    ValueCodec.writeEdit(out, edited.edit());
                }
                case SyncPacket.PunishmentVoidedPacket voided -> {
                    out.writeByte(VOIDED);
                    out.writeLong(voided.punishmentId());
                }
                case SyncPacket.PunishmentExpungedPacket expunged -> {
                    out.writeByte(EXPUNGED);
                    ValueCodec.writePunishment(out, expunged.punishment());
                }
                case SyncPacket.EvidenceChangedPacket evidence -> {
                    out.writeByte(EVIDENCE);
                    out.writeLong(evidence.punishmentId());
                    out.writeBoolean(evidence.attached());
                    ValueCodec.writeEvidence(out, evidence.evidence());
                }
                case SyncPacket.LockdownChangedPacket lockdown -> out.writeByte(LOCKDOWN);
                case SyncPacket.ImportedPacket imported -> {
                    out.writeByte(IMPORTED);
                    out.writeBoolean(imported.enforce());
                }
                case SyncPacket.PlayerDataErasedPacket erased -> {
                    out.writeByte(ERASED);
                    final SakuraPlayerData.Erase result = erased.result();
                    out.writeLong(result.uuid().getMostSignificantBits());
                    out.writeLong(result.uuid().getLeastSignificantBits());
                    for (final long count : new long[] {result.namesRemoved(),
                            result.addressesRemoved(), result.allowedAddressesRemoved(),
                            result.victimAddressesCleared(), result.evidenceRemoved(),
                            result.punishmentsKept(), result.namesRedacted()}) {
                        out.writeLong(count);
                    }
                    ValueCodec.writeActor(out, erased.actor());
                }
                case SyncPacket.PunishmentsRolledBackPacket rolledBack -> {
                    out.writeByte(ROLLED_BACK);
                    out.writeInt(rolledBack.punishmentIds().size());
                    for (final long id : rolledBack.punishmentIds()) {
                        out.writeLong(id);
                    }
                    ValueCodec.writeOperator(out, rolledBack.target());
                    out.writeLong(rolledBack.since().toEpochMilli());
                    ValueCodec.writeActor(out, rolledBack.actor());
                    out.writeBoolean(rolledBack.reasonOrNull() != null);
                    if (rolledBack.reasonOrNull() != null) {
                        out.writeUTF(rolledBack.reasonOrNull());
                    }
                    out.writeUTF(rolledBack.visibility().name());
                }
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
                case ENTITY_CHANGED -> Optional.of(new SyncPacket.EntityChangedPacket(
                        SubjectType.valueOf(in.readUTF()), in.readUTF(),
                        ChangeType.valueOf(in.readUTF()), in.readLong(),
                        ValueCodec.readActor(in)));
                case EDITED -> Optional.of(new SyncPacket.PunishmentEditedPacket(in.readLong(),
                        ValueCodec.readEdit(in), ValueCodec.readEdit(in)));
                case VOIDED -> Optional.of(new SyncPacket.PunishmentVoidedPacket(in.readLong()));
                case EXPUNGED -> Optional.of(new SyncPacket.PunishmentExpungedPacket(
                        ValueCodec.readPunishment(in)));
                case LOCKDOWN -> Optional.of(new SyncPacket.LockdownChangedPacket());
                case IMPORTED -> Optional.of(new SyncPacket.ImportedPacket(in.readBoolean()));
                case ERASED -> Optional.of(new SyncPacket.PlayerDataErasedPacket(
                        new SakuraPlayerData.Erase(new UUID(in.readLong(), in.readLong()),
                                in.readLong(), in.readLong(), in.readLong(), in.readLong(),
                                in.readLong(), in.readLong(), in.readLong()),
                        ValueCodec.readActor(in)));
                case ROLLED_BACK -> {
                    final int count = in.readInt();
                    final List<Long> ids = new ArrayList<>(count);
                    for (int i = 0; i < count; i++) {
                        ids.add(in.readLong());
                    }
                    final Operator target = ValueCodec.readOperator(in);
                    final Instant since = Instant.ofEpochMilli(in.readLong());
                    final Actor actor = ValueCodec.readActor(in);
                    final String reason = in.readBoolean() ? in.readUTF() : null;
                    yield Optional.of(new SyncPacket.PunishmentsRolledBackPacket(ids, target,
                            since, actor, reason, Visibility.valueOf(in.readUTF())));
                }
                case EVIDENCE -> {
                    final long id = in.readLong();
                    final boolean attached = in.readBoolean();
                    yield Optional.of(new SyncPacket.EvidenceChangedPacket(id, attached,
                            ValueCodec.readEvidence(in)));
                }
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
