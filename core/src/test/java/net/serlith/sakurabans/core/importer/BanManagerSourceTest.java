package net.serlith.sakurabans.core.importer;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import net.serlith.sakurabans.api.model.EndKind;
import net.serlith.sakurabans.api.model.PunishmentType;
import net.serlith.sakurabans.api.model.Visibility;
import net.serlith.sakurabans.core.model.SakuraImportReport;
import net.serlith.sakurabans.core.storage.dbo.OperatorKind;
import net.serlith.sakurabans.core.storage.dbo.SakuraTables;
import net.serlith.sakurabans.core.storage.dbo.VictimKind;
import net.serlith.sakurabans.core.storage.dbo.records.SbPunishmentsRecord;
import org.assertj.core.api.Assertions;
import org.jooq.Record3;
import org.junit.jupiter.api.Test;

class BanManagerSourceTest extends ImportTest {

    private static final UUID CONSOLE = UUID.fromString("09130bc4-b0cd-4e26-8e13-a1c1d682a2b0");
    private static final UUID BAN_MOD = UUID.fromString("0f6b8d6a-1d34-2b4e-9c1e-5b7a1e1d2c3b");
    private static final UUID CARA = UUID.fromString("394aef83-6e3f-482b-9988-933cabe3b1cd");
    private static final Instant FUTURE = Instant.ofEpochSecond(2147483000L);

    @Override
    protected ImportSources sources() {
        return new ImportSources(List.of(new BanManagerSource()));
    }

    private void fixture() {
        this.fixture(Engine.H2, "banmanager", "bm_", ";MODE=MySQL")
                .script("banmanager/fixture.sql");
    }

    private SbPunishmentsRecord stored(final String externalId) {
        return this.read(dsl -> dsl.selectFrom(SakuraTables.PUNISHMENTS)
                .where(SakuraTables.PUNISHMENTS.EXTERNAL_ID.eq("banmanager:" + externalId))
                .fetchSingle());
    }

    private Record3<VictimKind, UUID, byte[]> victim(final SbPunishmentsRecord punishment) {
        return this.read(dsl -> dsl.select(SakuraTables.VICTIMS.KIND, SakuraTables.VICTIMS.UUID,
                        SakuraTables.VICTIMS.ADDRESS).from(SakuraTables.VICTIMS)
                .where(SakuraTables.VICTIMS.ID.eq(punishment.victimId())).fetchSingle());
    }

    private static byte[] address(final String literal) throws UnknownHostException {
        return InetAddress.getByName(literal).getAddress();
    }

    @Test
    void activePunishmentsRecordsPlayersAndOperatorsAreImportedOnce()
            throws UnknownHostException {
        this.fixture();

        Assertions.assertThat(this.jobs().survey("banmanager").join())
                .isInstanceOfSatisfying(ImportOutcome.Surveyed.class, surveyed ->
                        Assertions.assertThat(surveyed.rows())
                                .containsEntry("players", 5L)
                                .containsEntry("player_bans", 4L)
                                .containsEntry("player_ban_records", 4L)
                                .containsEntry("ip_bans", 3L)
                                .containsEntry("player_kicks", 1L)
                                .doesNotContainKey("name_bans")
                                .doesNotContainKey("ip_range_bans"));
        final SakuraImportReport report = this.importFrom("banmanager");

        Assertions.assertThat(report.imported()).isEqualTo(19);
        Assertions.assertThat(report.failed()).isEqualTo(2);
        Assertions.assertThat(report.players()).isEqualTo(4);
        Assertions.assertThat(report.failures()).extracting(failure -> failure.externalId().get())
                .containsExactlyInAnyOrder("banmanager:player_bans:4", "banmanager:ip_bans:3");
        Assertions.assertThat(this.count(SakuraTables.PUNISHMENTS)).isEqualTo(19);

        // The console is the oldest player row: no player of its own.
        Assertions.assertThat(this.count(SakuraTables.PLAYER_NAMES)).isEqualTo(4);
        Assertions.assertThat(this.count(SakuraTables.PLAYER_NAMES,
                SakuraTables.PLAYER_NAMES.UUID.eq(CONSOLE))).isZero();
        Assertions.assertThat(this.count(SakuraTables.PLAYER_NAMES,
                SakuraTables.PLAYER_NAMES.UUID.eq(STEVE)
                        .and(SakuraTables.PLAYER_NAMES.NAME.eq("Steve")))).isEqualTo(1);
        Assertions.assertThat(this.count(SakuraTables.PLAYER_ADDRESSES)).isEqualTo(4);
        Assertions.assertThat(this.count(SakuraTables.PLAYER_ADDRESSES,
                SakuraTables.PLAYER_ADDRESSES.UUID.eq(ALEX).and(
                        SakuraTables.PLAYER_ADDRESSES.ADDRESS.eq(
                                InetAddress.getByName("2001:db8::1"))))).isEqualTo(1);

        final SbPunishmentsRecord steve = this.stored("player_bans:1");
        Assertions.assertThat(steve.type()).isEqualTo(PunishmentType.BAN);
        Assertions.assertThat(steve.operatorKind()).isEqualTo(OperatorKind.CONSOLE);
        Assertions.assertThat(steve.reason()).isEqualTo("Griefing the spawn");
        Assertions.assertThat(steve.visibility()).isEqualTo(Visibility.SILENT);
        Assertions.assertThat(steve.expiresAt()).isNull();
        Assertions.assertThat(steve.endKind()).isNull();
        Assertions.assertThat(steve.createdAt()).isEqualTo(Instant.parse("2026-10-01T12:00:00Z"));
        Assertions.assertThat(this.victim(steve).value2()).isEqualTo(STEVE);

        final SbPunishmentsRecord alex = this.stored("player_bans:2");
        Assertions.assertThat(alex.operatorKind()).isEqualTo(OperatorKind.PLAYER);
        Assertions.assertThat(alex.operatorUuid()).isEqualTo(BAN_MOD);
        Assertions.assertThat(alex.operatorName()).isEqualTo("Mod");
        Assertions.assertThat(alex.expiresAt()).isEqualTo(FUTURE);
        Assertions.assertThat(alex.visibility()).isEqualTo(Visibility.NORMAL);

        final SbPunishmentsRecord oldBan = this.stored("player_bans:3");
        Assertions.assertThat(oldBan.endKind()).isEqualTo(EndKind.EXPIRED);
        Assertions.assertThat(oldBan.endedAt()).isEqualTo(Instant.parse("2026-09-01T00:00:00Z"));

        final SbPunishmentsRecord appealed = this.stored("player_ban_records:1");
        Assertions.assertThat(appealed.endKind()).isEqualTo(EndKind.REVOKED);
        Assertions.assertThat(appealed.endedAt()).isEqualTo(Instant.parse("2026-06-20T11:00:00Z"));
        Assertions.assertThat(appealed.revokerKind()).isEqualTo(OperatorKind.PLAYER);
        Assertions.assertThat(appealed.revokerUuid()).isEqualTo(BAN_MOD);
        Assertions.assertThat(appealed.revokeReason()).isEqualTo("appealed");
        Assertions.assertThat(appealed.operatorKind()).isEqualTo(OperatorKind.CONSOLE);
        Assertions.assertThat(appealed.createdAt())
                .isEqualTo(Instant.parse("2026-06-01T10:00:00Z"));
        Assertions.assertThat(appealed.expiresAt()).isNull();

        final SbPunishmentsRecord undone = this.stored("player_ban_records:2");
        Assertions.assertThat(undone.endKind()).isEqualTo(EndKind.REVOKED);
        Assertions.assertThat(undone.revokerKind()).isEqualTo(OperatorKind.CONSOLE);
        Assertions.assertThat(undone.revokeReason()).isNull();
        Assertions.assertThat(undone.operatorUuid()).isEqualTo(BAN_MOD);
        Assertions.assertThat(undone.expiresAt()).isEqualTo(FUTURE);

        final SbPunishmentsRecord expired = this.stored("player_ban_records:3");
        Assertions.assertThat(expired.endKind()).isEqualTo(EndKind.EXPIRED);
        Assertions.assertThat(expired.endedAt()).isEqualTo(Instant.parse("2026-07-11T10:00:00Z"));
        Assertions.assertThat(expired.revokerKind()).isNull();

        final SbPunishmentsRecord gone = this.stored("player_ban_records:4");
        Assertions.assertThat(gone.operatorKind()).isEqualTo(OperatorKind.CUSTOM);
        Assertions.assertThat(gone.operatorName()).isEqualTo(
                "aaaaaaaa-aaaa-4aaa-aaaa-aaaaaaaaaaaa");
        Assertions.assertThat(gone.revokerUuid()).isEqualTo(BAN_MOD);
        Assertions.assertThat(gone.revokeReason()).isEqualTo("mistake");

        final Record3<VictimKind, UUID, byte[]> ip = this.victim(this.stored("ip_bans:1"));
        Assertions.assertThat(ip.value1()).isEqualTo(VictimKind.ADDRESS);
        Assertions.assertThat(ip.value3()).isEqualTo(address("203.0.113.50"));
        final SbPunishmentsRecord v6 = this.stored("ip_bans:2");
        Assertions.assertThat(this.victim(v6).value3()).isEqualTo(address("2001:db8::1"));
        Assertions.assertThat(v6.expiresAt()).isEqualTo(FUTURE);
        Assertions.assertThat(this.stored("ip_ban_records:1").endKind())
                .isEqualTo(EndKind.REVOKED);
        Assertions.assertThat(this.victim(this.stored("ip_ban_records:1")).value3())
                .isEqualTo(address("192.0.2.44"));

        final SbPunishmentsRecord soft = this.stored("player_mutes:1");
        Assertions.assertThat(soft.type()).isEqualTo(PunishmentType.MUTE);
        Assertions.assertThat(soft.kind()).isEqualTo("shadow-mute");
        Assertions.assertThat(this.stored("player_mutes:2").kind()).isEqualTo("mute");
        Assertions.assertThat(this.stored("player_mute_records:1").endKind())
                .isEqualTo(EndKind.REVOKED);
        Assertions.assertThat(this.stored("ip_mutes:1").endKind()).isNull();
        Assertions.assertThat(this.victim(this.stored("ip_mutes:1")).value3())
                .isEqualTo(address("203.0.113.60"));
        Assertions.assertThat(this.stored("ip_mute_records:1").endKind())
                .isEqualTo(EndKind.REVOKED);

        Assertions.assertThat(this.stored("player_warnings:1").type())
                .isEqualTo(PunishmentType.WARN);
        Assertions.assertThat(this.stored("player_warnings:2").expiresAt()).isEqualTo(FUTURE);
        Assertions.assertThat(this.stored("player_warnings:2").operatorKind())
                .isEqualTo(OperatorKind.CONSOLE);
        Assertions.assertThat(this.stored("player_warnings:3").operatorKind())
                .isEqualTo(OperatorKind.CUSTOM);
        final SbPunishmentsRecord kick = this.stored("player_kicks:1");
        Assertions.assertThat(kick.type()).isEqualTo(PunishmentType.KICK);
        Assertions.assertThat(kick.endKind()).isNull();
        Assertions.assertThat(this.victim(kick).value2()).isEqualTo(CARA);

        final SakuraImportReport second = this.importFrom("banmanager");
        Assertions.assertThat(second.imported()).isZero();
        Assertions.assertThat(second.skipped()).isEqualTo(19);
        Assertions.assertThat(this.count(SakuraTables.PUNISHMENTS)).isEqualTo(19);
    }

    @Test
    void dryRunsWriteNothing() {
        this.fixture();

        final SakuraImportReport report = this.importFrom("banmanager", true);

        Assertions.assertThat(report.dryRun()).isTrue();
        Assertions.assertThat(report.imported()).isEqualTo(19);
        Assertions.assertThat(report.failed()).isEqualTo(2);
        Assertions.assertThat(this.count(SakuraTables.PUNISHMENTS)).isZero();
        Assertions.assertThat(this.count(SakuraTables.PLAYER_NAMES)).isZero();
    }

    @Test
    void olderSchemasWithIntegerAddressesAndMissingTablesAreRead() {
        this.fixture(Engine.H2, "banmanager", "bm_", ";MODE=MySQL")
                .execute("CREATE TABLE \"bm_players\" (\"id\" BINARY(16) NOT NULL, "
                        + "\"name\" VARCHAR(16) NOT NULL, \"ip\" BIGINT NOT NULL, "
                        + "\"lastSeen\" INT NOT NULL)")
                .execute("CREATE TABLE \"bm_ip_bans\" (\"id\" INT NOT NULL, "
                        + "\"ip\" BIGINT NOT NULL, \"reason\" VARCHAR(255) NOT NULL, "
                        + "\"actor_id\" BINARY(16) NOT NULL, \"created\" INT NOT NULL, "
                        + "\"updated\" INT NOT NULL, \"expires\" INT NOT NULL)")
                .execute("INSERT INTO \"bm_players\" VALUES "
                        + "(X'09130bc4b0cd4e268e13a1c1d682a2b0', 'Console', 2130706433, 1000), "
                        + "(X'8667ba71b85a4004af54457a9734eed7', 'Steve', 3405803783, 2000)")
                .execute("INSERT INTO \"bm_ip_bans\" VALUES (1, 3405803783, 'Integer address', "
                        + "X'09130bc4b0cd4e268e13a1c1d682a2b0', 1790856000, 1790856000, 0)");

        final SakuraImportReport report = this.importFrom("banmanager");

        Assertions.assertThat(report.imported()).isEqualTo(1);
        Assertions.assertThat(report.failed()).isZero();
        final SbPunishmentsRecord ban = this.stored("ip_bans:1");
        Assertions.assertThat(ban.operatorKind()).isEqualTo(OperatorKind.CONSOLE);
        Assertions.assertThat(this.victim(ban).value3())
                .isEqualTo(new byte[] {(byte) 203, 0, 113, 7});
        Assertions.assertThat(this.count(SakuraTables.PLAYER_ADDRESSES)).isEqualTo(1);
    }
}
