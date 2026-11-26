package net.serlith.sakurabans.core.sync;

import java.net.InetAddress;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import net.serlith.sakurabans.api.model.Actor;
import net.serlith.sakurabans.api.model.AddressStrictness;
import net.serlith.sakurabans.api.model.CategoryScope;
import net.serlith.sakurabans.api.model.CompositeVictim;
import net.serlith.sakurabans.api.model.ConsoleOperator;
import net.serlith.sakurabans.api.model.CustomOperator;
import net.serlith.sakurabans.api.model.EndKind;
import net.serlith.sakurabans.api.model.ErasedAddressVictim;
import net.serlith.sakurabans.api.model.EvidenceRef;
import net.serlith.sakurabans.api.model.GlobalScope;
import net.serlith.sakurabans.api.model.Lengths;
import net.serlith.sakurabans.api.model.PlayerOperator;
import net.serlith.sakurabans.api.model.Punishment;
import net.serlith.sakurabans.api.model.PunishmentType;
import net.serlith.sakurabans.api.model.ServerScope;
import net.serlith.sakurabans.api.model.Visibility;
import net.serlith.sakurabans.api.punish.PunishmentEdit;
import net.serlith.sakurabans.core.RecordingLogger;
import net.serlith.sakurabans.core.model.SakuraEvidence;
import net.serlith.sakurabans.core.model.SakuraPlayerData;
import net.serlith.sakurabans.core.model.SakuraPunishment;
import net.serlith.sakurabans.core.model.SakuraPunishmentEnd;
import net.serlith.sakurabans.core.model.SakuraPunishmentVoid;
import net.serlith.sakurabans.core.model.SakuraTemplateRef;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

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
                new SyncPacket.AllowListChangedPacket(),
                new SyncPacket.PunishmentEditedPacket(4, PunishmentEdit.NewReason.of("old"),
                        PunishmentEdit.NewReason.of("new")),
                new SyncPacket.PunishmentEditedPacket(4, PunishmentEdit.NewExpiry.of(null),
                        PunishmentEdit.NewExpiry.of(Instant.ofEpochMilli(5_000))),
                new SyncPacket.PunishmentEditedPacket(4,
                        PunishmentEdit.NewScope.of(GlobalScope.INSTANCE),
                        PunishmentEdit.NewScope.of(CategoryScope.of("pvp"))),
                new SyncPacket.PunishmentVoidedPacket(5),
                new SyncPacket.LockdownChangedPacket(),
                new SyncPacket.ImportedPacket(true),
                new SyncPacket.ImportedPacket(false),
                new SyncPacket.PunishmentsRolledBackPacket(List.of(1L, 2L),
                        PlayerOperator.of(UUID.randomUUID(), "Mod"), Instant.ofEpochMilli(7),
                        Actor.of(ConsoleOperator.INSTANCE, "console"), null, Visibility.SILENT),
                new SyncPacket.PlayerDataErasedPacket(new SakuraPlayerData.Erase(
                        UUID.randomUUID(), 1, 2, 3, 4, 5, 6, 7),
                        Actor.of(ConsoleOperator.INSTANCE, "console")),
                new SyncPacket.EvidenceChangedPacket(6, true, new SakuraEvidence(
                        EvidenceRef.of("hydra:chat:1"), Actor.of(CustomOperator.of("Hydra",
                                null), "api:hydra"), Instant.ofEpochMilli(9_000)))}) {
            Assertions.assertThat(this.receiver.decode(this.sender.encode(packet)))
                    .contains(packet);
        }
    }

    @Test
    void expungedPunishmentsTravelWhole() throws Exception {
        final Punishment punishment = new SakuraPunishment(12, "K7Q2M9XA", PunishmentType.BAN,
                "competitive-ban", false, AddressStrictness.STRICT,
                CompositeVictim.of(UUID.randomUUID(), InetAddress.getByName("2001:db8::1")),
                PlayerOperator.of(UUID.randomUUID(), "Mod"), "in-game",
                ServerScope.of("survival"), "x".repeat(Lengths.MAX_REASON),
                Instant.ofEpochMilli(1_000), Instant.ofEpochMilli(2_000), "lobby",
                Visibility.HIDDEN, new SakuraTemplateRef("hacking", 2),
                new SakuraPunishmentEnd(EndKind.VOIDED, Instant.ofEpochMilli(1_500),
                        Actor.of(ConsoleOperator.INSTANCE, "console"), "é".repeat(1024)),
                new SakuraPunishmentVoid(Instant.ofEpochMilli(1_500),
                        Actor.of(ConsoleOperator.INSTANCE, "console"), "€".repeat(1024)),
                List.of(), 4);
        final SyncPacket packet = new SyncPacket.PunishmentExpungedPacket(punishment);
        final byte[] bytes = this.sender.encode(packet);

        Assertions.assertThat(bytes.length).isLessThanOrEqualTo(16_384);
        Assertions.assertThat(this.receiver.decode(bytes)).contains(packet);
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void erasedVictimsTravel(final boolean composite) {
        final Punishment punishment = new SakuraPunishment(13, "K7Q2M9XB", PunishmentType.BAN,
                "ban", false, null,
                ErasedAddressVictim.of("0123456789abcdef".repeat(4),
                        composite ? UUID.randomUUID() : null),
                PlayerOperator.of(UUID.randomUUID(), "erased"), "in-game", GlobalScope.INSTANCE,
                "reason", Instant.ofEpochMilli(1_000), null, null, Visibility.NORMAL, null, null,
                null, List.of(), 1);
        final SyncPacket packet = new SyncPacket.PunishmentExpungedPacket(punishment);

        Assertions.assertThat(this.receiver.decode(this.sender.encode(packet))).contains(packet);
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
