package net.serlith.sakurabans.core.sync;

import java.util.UUID;
import net.serlith.sakurabans.api.model.Visibility;
import net.serlith.sakurabans.core.RecordingLogger;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

class PacketCodecTest {

    private final RecordingLogger log = new RecordingLogger();
    private final PacketCodec sender = new PacketCodec(UUID.randomUUID(), this.log.logger());
    private final PacketCodec receiver = new PacketCodec(UUID.randomUUID(), this.log.logger());

    @Test
    void everyPacketRoundTrips() {
        for (final SyncPacket packet : new SyncPacket[] {
                SyncPacket.PunishmentEnforcePacket.of(7),
                new SyncPacket.PunishmentEnforcePacket(8, 3L),
                new SyncPacket.PunishmentRevokePacket(9, Visibility.SILENT),
                new SyncPacket.AllowListChangedPacket()}) {
            Assertions.assertThat(this.receiver.decode(this.sender.encode(packet)))
                    .contains(packet);
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
