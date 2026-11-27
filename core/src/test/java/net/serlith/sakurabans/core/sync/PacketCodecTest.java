package net.serlith.sakurabans.core.sync;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import net.serlith.sakurabans.api.model.Visibility;
import net.serlith.sakurabans.core.RecordingLogger;
import net.serlith.sakurabans.core.model.SubjectType;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

class PacketCodecTest {

    private static final List<SyncPacket> PACKETS = List.of(
            SyncPacket.PunishmentEnforcePacket.of(7),
            new SyncPacket.PunishmentEnforcePacket(8, 3L),
            new SyncPacket.PunishmentRevokePacket(9, Visibility.SILENT),
            new SyncPacket.PunishmentRevokePacket(9, Visibility.HIDDEN),
            new SyncPacket.PunishmentEditedPacket(4, true),
            new SyncPacket.PunishmentEditedPacket(4, false),
            new SyncPacket.PunishmentsEndedPacket(List.of(5L)),
            new SyncPacket.PunishmentsEndedPacket(List.of(1L, 2L, Long.MAX_VALUE)),
            new SyncPacket.PunishmentExpungedPacket(12, true),
            new SyncPacket.PunishmentExpungedPacket(13, false),
            new SyncPacket.PlayerDataErasedPacket(UUID.randomUUID()),
            new SyncPacket.AllowListChangedPacket(),
            new SyncPacket.LockdownChangedPacket(),
            new SyncPacket.EntityChangedPacket(SubjectType.TEMPLATE),
            new SyncPacket.EntityChangedPacket(SubjectType.SETTINGS),
            new SyncPacket.EntityChangedPacket(SubjectType.MESSAGE),
            new SyncPacket.ImportedPacket(),
            new SyncPacket.PlayerKickedPacket(
                    UUID.fromString("0f6b8d6a-1d34-4b2e-9c1e-5b7a1e1d2c3b"), "Restarting"),
            new SyncPacket.PunishmentsResetPacket(),
            new SyncPacket.AltsAutoBannedPacket(7L, List.of("Alex", "Bob")));

    private final RecordingLogger log = new RecordingLogger();
    private final PacketCodec sender = new PacketCodec(UUID.randomUUID(), this.log.logger());
    private final PacketCodec receiver = new PacketCodec(UUID.randomUUID(), this.log.logger());

    @Test
    void everyPacketRoundTrips() {
        for (final SyncPacket packet : PACKETS) {
            Assertions.assertThat(this.receiver.decode(this.sender.encode(packet)))
                    .contains(packet);
        }
        Assertions.assertThat(PACKETS).extracting(Object::getClass).map(Class.class::cast)
                .containsAll(Arrays.asList(SyncPacket.class.getPermittedSubclasses()));
        Assertions.assertThat(this.log.entries("warn")).isEmpty();
    }

    @Test
    void packetsStaySmall() {
        for (final SyncPacket packet : PACKETS) {
            Assertions.assertThat(this.sender.encode(packet).length).as("%s", packet)
                    .isLessThanOrEqualTo(64);
        }
    }

    @Test
    void ownPacketsAreSkipped() {
        Assertions.assertThat(this.sender.decode(
                this.sender.encode(SyncPacket.PunishmentEnforcePacket.of(1)))).isEmpty();
    }

    @Test
    void otherVersionsAndGarbageAreIgnoredWithWarnings() {
        final byte[] packet = this.sender.encode(SyncPacket.PunishmentEnforcePacket.of(1));
        packet[0] = 9;

        Assertions.assertThat(this.receiver.decode(packet)).isEmpty();
        Assertions.assertThat(this.receiver.decode(new byte[] {1, 2})).isEmpty();
        Assertions.assertThat(this.log.entries("warn")).hasSize(2);
    }
}
