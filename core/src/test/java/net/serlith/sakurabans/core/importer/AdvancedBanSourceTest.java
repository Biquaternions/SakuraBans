package net.serlith.sakurabans.core.importer;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.serlith.sakurabans.api.model.EndKind;
import net.serlith.sakurabans.api.model.PunishmentType;
import net.serlith.sakurabans.core.model.SakuraImportReport;
import net.serlith.sakurabans.core.storage.dbo.OperatorKind;
import net.serlith.sakurabans.core.storage.dbo.SakuraTables;
import net.serlith.sakurabans.core.storage.dbo.VictimKind;
import net.serlith.sakurabans.core.storage.dbo.records.SbPunishmentsRecord;
import net.serlith.sakurabans.core.util.Uuids;
import org.assertj.core.api.Assertions;
import org.jooq.Record3;
import org.junit.jupiter.api.Test;

class AdvancedBanSourceTest extends ImportTest {

    private static final UUID BOB = UUID.fromString("0f6b8d6a-1d34-2b4e-9c1e-5b7a1e1d2c3b");
    private static final UUID FAY = UUID.fromString("00140fe8-de08-41e9-ab79-e53b9f3f0fbd");
    private static final Instant FUTURE = Instant.parse("2099-01-01T00:00:00Z");

    @Override
    protected ImportSources sources() {
        return new ImportSources(List.of(new AdvancedBanSource(this.clock)));
    }

    private void fixture() {
        this.fixture(Engine.HSQLDB, "advancedban", "").script("advancedban/fixture.sql");
    }

    private SbPunishmentsRecord stored(final String externalId) {
        return this.read(dsl -> dsl.selectFrom(SakuraTables.PUNISHMENTS)
                .where(SakuraTables.PUNISHMENTS.EXTERNAL_ID.eq("advancedban:" + externalId))
                .fetchSingle());
    }

    private Record3<VictimKind, UUID, byte[]> victim(final SbPunishmentsRecord punishment) {
        return this.read(dsl -> dsl.select(SakuraTables.VICTIMS.KIND, SakuraTables.VICTIMS.UUID,
                        SakuraTables.VICTIMS.ADDRESS).from(SakuraTables.VICTIMS)
                .where(SakuraTables.VICTIMS.ID.eq(punishment.victimId())).fetchSingle());
    }

    private String name(final UUID uuid) {
        return this.read(dsl -> dsl.select(SakuraTables.PLAYER_NAMES.NAME)
                .from(SakuraTables.PLAYER_NAMES)
                .where(SakuraTables.PLAYER_NAMES.UUID.eq(uuid)).fetchSingle().value1());
    }

    private static byte[] address(final String literal) throws UnknownHostException {
        return InetAddress.getByName(literal).getAddress();
    }

    @Test
    void activeAndHistoricalPunishmentsAreImportedOnceEach() throws UnknownHostException {
        this.fixture();

        Assertions.assertThat(this.jobs().survey("advancedban").join())
                .isEqualTo(new ImportOutcome.Surveyed(Map.of("Punishments", 10L,
                        "PunishmentHistory", 18L)));
        final SakuraImportReport report = this.importFrom("advancedban");

        // 8 active + 5 only in the history; the 8 copies are the same punishments.
        Assertions.assertThat(report.imported()).isEqualTo(13);
        Assertions.assertThat(this.count(SakuraTables.PUNISHMENTS)).isEqualTo(13);
        Assertions.assertThat(report.failed()).isEqualTo(4);
        Assertions.assertThat(report.failures()).extracting(failure -> failure.externalId().get())
                .containsExactlyInAnyOrder("advancedban:Punishments:9",
                        "advancedban:PunishmentHistory:29",
                        "advancedban:PunishmentHistory:36",
                        "advancedban:PunishmentHistory:37");

        final SbPunishmentsRecord steve = this.stored("punishments:1");
        Assertions.assertThat(steve.type()).isEqualTo(PunishmentType.BAN);
        Assertions.assertThat(steve.operatorKind()).isEqualTo(OperatorKind.CONSOLE);
        Assertions.assertThat(steve.reason()).isEqualTo("Griefing the spawn");
        Assertions.assertThat(steve.createdAt()).isEqualTo(Instant.parse("2026-10-01T12:00:00Z"));
        Assertions.assertThat(steve.expiresAt()).isNull();
        Assertions.assertThat(steve.endKind()).isNull();
        Assertions.assertThat(this.victim(steve).value2()).isEqualTo(STEVE);
        Assertions.assertThat(this.name(STEVE)).isEqualTo("Steve");

        final SbPunishmentsRecord alex = this.stored("punishments:2");
        Assertions.assertThat(alex.operatorKind()).isEqualTo(OperatorKind.CUSTOM);
        Assertions.assertThat(alex.operatorName()).isEqualTo("Mod");
        Assertions.assertThat(alex.operatorUuid()).isNull();
        Assertions.assertThat(alex.expiresAt()).isEqualTo(FUTURE);
        Assertions.assertThat(alex.endKind()).isNull();

        final UUID offline = Uuids.offline("OfflineGuy");
        Assertions.assertThat(this.victim(this.stored("punishments:3")).value2())
                .isEqualTo(offline);
        Assertions.assertThat(this.name(offline)).isEqualTo("OfflineGuy");

        final Record3<VictimKind, UUID, byte[]> ip = this.victim(this.stored("punishments:4"));
        Assertions.assertThat(ip.value1()).isEqualTo(VictimKind.ADDRESS);
        Assertions.assertThat(ip.value3()).isEqualTo(address("203.0.113.50"));
        Assertions.assertThat(this.stored("punishments:4").operatorKind())
                .isEqualTo(OperatorKind.CONSOLE);
        final SbPunishmentsRecord tempIp = this.stored("punishments:5");
        Assertions.assertThat(this.victim(tempIp).value3()).isEqualTo(address("2001:db8::1"));
        Assertions.assertThat(tempIp.expiresAt()).isEqualTo(FUTURE);

        Assertions.assertThat(this.stored("punishments:6").type()).isEqualTo(PunishmentType.MUTE);
        Assertions.assertThat(this.victim(this.stored("punishments:6")).value2()).isEqualTo(BOB);
        Assertions.assertThat(this.stored("punishments:7").type()).isEqualTo(PunishmentType.WARN);
        final SbPunishmentsRecord expired = this.stored("punishments:8");
        Assertions.assertThat(expired.endKind()).isEqualTo(EndKind.EXPIRED);
        Assertions.assertThat(expired.endedAt()).isEqualTo(Instant.parse("2026-09-10T00:00:00Z"));

        // Only in the history and still running: removed, by someone unknown, found out now.
        final SbPunishmentsRecord removed = this.stored("history:31");
        Assertions.assertThat(removed.endKind()).isEqualTo(EndKind.REVOKED);
        Assertions.assertThat(removed.endedAt()).isEqualTo(this.now.get());
        Assertions.assertThat(removed.revokerKind()).isEqualTo(OperatorKind.CUSTOM);
        Assertions.assertThat(removed.revokerName()).isEqualTo("unknown");
        Assertions.assertThat(removed.expiresAt()).isNull();
        final SbPunishmentsRecord expiredInHistory = this.stored("history:32");
        Assertions.assertThat(expiredInHistory.endKind()).isEqualTo(EndKind.EXPIRED);
        Assertions.assertThat(expiredInHistory.endedAt())
                .isEqualTo(Instant.parse("2026-10-01T00:00:00Z"));
        final SbPunishmentsRecord removedTemp = this.stored("history:33");
        Assertions.assertThat(removedTemp.endKind()).isEqualTo(EndKind.REVOKED);
        Assertions.assertThat(removedTemp.expiresAt()).isEqualTo(FUTURE);
        final SbPunishmentsRecord kick = this.stored("history:34");
        Assertions.assertThat(kick.type()).isEqualTo(PunishmentType.KICK);
        Assertions.assertThat(kick.endKind()).isNull();
        Assertions.assertThat(this.victim(kick).value2()).isEqualTo(FAY);
        final SbPunishmentsRecord removedIp = this.stored("history:35");
        Assertions.assertThat(removedIp.endKind()).isEqualTo(EndKind.REVOKED);
        Assertions.assertThat(this.victim(removedIp).value3()).isEqualTo(address("198.51.100.9"));

        final SakuraImportReport second = this.importFrom("advancedban");
        Assertions.assertThat(second.imported()).isZero();
        Assertions.assertThat(second.skipped()).isEqualTo(13);
        Assertions.assertThat(this.count(SakuraTables.PUNISHMENTS)).isEqualTo(13);
    }

    @Test
    void dryRunsWriteNothing() {
        this.fixture();

        final SakuraImportReport report = this.importFrom("advancedban", true);

        Assertions.assertThat(report.dryRun()).isTrue();
        Assertions.assertThat(report.imported()).isEqualTo(13);
        Assertions.assertThat(report.failed()).isEqualTo(4);
        Assertions.assertThat(this.count(SakuraTables.PUNISHMENTS)).isZero();
        Assertions.assertThat(this.count(SakuraTables.PLAYER_NAMES)).isZero();
    }
}
