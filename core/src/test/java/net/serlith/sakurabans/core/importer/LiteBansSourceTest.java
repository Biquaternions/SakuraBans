package net.serlith.sakurabans.core.importer;

import java.net.InetAddress;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.serlith.sakurabans.api.imports.ImportReport;
import net.serlith.sakurabans.api.model.AddressRangeVictim;
import net.serlith.sakurabans.api.model.AddressRecord;
import net.serlith.sakurabans.api.model.AddressVictim;
import net.serlith.sakurabans.api.model.CompositeVictim;
import net.serlith.sakurabans.api.model.ConsoleOperator;
import net.serlith.sakurabans.api.model.CustomOperator;
import net.serlith.sakurabans.api.model.EndKind;
import net.serlith.sakurabans.api.model.GlobalScope;
import net.serlith.sakurabans.api.model.NameRecord;
import net.serlith.sakurabans.api.model.PlayerOperator;
import net.serlith.sakurabans.api.model.PlayerVictim;
import net.serlith.sakurabans.api.model.Punishment;
import net.serlith.sakurabans.api.model.PunishmentEnd;
import net.serlith.sakurabans.api.model.PunishmentType;
import net.serlith.sakurabans.api.model.ServerScope;
import net.serlith.sakurabans.api.model.Visibility;
import net.serlith.sakurabans.core.model.SakuraImportReport;
import net.serlith.sakurabans.core.storage.dbo.SakuraTables;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

/** {@link LiteBansSource} on H2 in MySQL mode (plan S40). */
class LiteBansSourceTest extends ImportTest {

    private static final UUID HEROBRINE = UUID.fromString("3f0d2a3c-1b5e-4c3a-9d6e-2a1b3c4d5e6f");
    private static final PlayerOperator MOD_OPERATOR = PlayerOperator.of(MOD, "Mod");

    @Override
    protected ImportSources sources() {
        return new ImportSources(List.of(new LiteBansSource()));
    }

    private void fill() {
        this.fixture(Engine.H2, "litebans", "litebans_", ";MODE=MySQL")
                .script("litebans/fixture.sql");
    }

    private Punishment punishment(final String table, final int id) {
        final String externalId = "litebans:" + table + ":" + id;
        final long stored = this.read(dsl -> dsl.select(SakuraTables.PUNISHMENTS.ID)
                .from(SakuraTables.PUNISHMENTS)
                .where(SakuraTables.PUNISHMENTS.EXTERNAL_ID.eq(externalId))
                .fetchSingle().value1());
        return this.punishments.byId(stored).join().orElseThrow();
    }

    private static Instant millis(final long millis) {
        return Instant.ofEpochMilli(millis);
    }

    // LiteBans' SQL timestamps, read in the JVM's zone like the source reads them.
    private static Instant timestamp(final String local) {
        return LocalDateTime.parse(local).atZone(ZoneId.systemDefault()).toInstant();
    }

    private static InetAddress ip(final String literal) throws Exception {
        return InetAddress.getByName(literal);
    }

    @Test
    void surveysEveryTable() {
        this.fill();

        Assertions.assertThat(this.jobs().survey("litebans").join()).isEqualTo(
                new ImportOutcome.Surveyed(Map.of("bans", 13L, "mutes", 2L, "warnings", 2L,
                        "kicks", 2L, "history", 8L)));
    }

    @Test
    void importsEveryPunishmentOnce() throws Exception {
        this.fill();

        final SakuraImportReport first = this.importFrom("litebans");

        Assertions.assertThat(first.imported()).isEqualTo(16);
        Assertions.assertThat(first.failed()).isEqualTo(4);
        Assertions.assertThat(first.failures()).extracting(f -> f.externalId().orElseThrow())
                .containsExactlyInAnyOrder("litebans:history:8", "litebans:bans:5",
                        "litebans:bans:10", "litebans:bans:11");
        Assertions.assertThat(first.truncated()).isEqualTo(1);

        final Punishment permanent = this.punishment("bans", 1);
        Assertions.assertThat(permanent.type()).isEqualTo(PunishmentType.BAN);
        Assertions.assertThat(permanent.victim()).isEqualTo(PlayerVictim.of(STEVE));
        Assertions.assertThat(permanent.operator()).isEqualTo(MOD_OPERATOR);
        Assertions.assertThat(permanent.reason()).isEqualTo("Hacking fly");
        Assertions.assertThat(permanent.createdAt()).isEqualTo(millis(1711965600000L));
        Assertions.assertThat(permanent.expiresAt()).isEmpty();
        Assertions.assertThat(permanent.scope()).isEqualTo(GlobalScope.INSTANCE);
        Assertions.assertThat(permanent.originServer()).contains("lobby");
        Assertions.assertThat(permanent.visibility()).isEqualTo(Visibility.NORMAL);
        Assertions.assertThat(permanent.end()).isEmpty();

        final Punishment ipBan = this.punishment("bans", 2);
        Assertions.assertThat(ipBan.victim())
                .isEqualTo(CompositeVictim.of(ALEX, ip("198.51.100.7")));
        Assertions.assertThat(ipBan.operator()).isEqualTo(ConsoleOperator.INSTANCE);
        Assertions.assertThat(ipBan.expiresAt()).contains(millis(1893456000000L));
        Assertions.assertThat(ipBan.scope()).isEqualTo(ServerScope.of("kitpvp"));
        Assertions.assertThat(ipBan.visibility()).isEqualTo(Visibility.SILENT);
        Assertions.assertThat(ipBan.end()).isEmpty();

        final Punishment addressOnly = this.punishment("bans", 3);
        Assertions.assertThat(addressOnly.victim()).isEqualTo(AddressVictim.of(ip("198.51.100.8")));
        Assertions.assertThat(addressOnly.operator()).isEqualTo(CustomOperator.of("Notch", null));
        Assertions.assertThat(addressOnly.originServer()).isEmpty();

        Assertions.assertThat(this.punishment("bans", 4).victim())
                .isEqualTo(AddressRangeVictim.of(ip("10.0.0.0"), 16));
        Assertions.assertThat(this.punishment("bans", 13).victim())
                .isEqualTo(PlayerVictim.of(STEVE));
        Assertions.assertThat(this.punishment("bans", 13).scope())
                .isEqualTo(ServerScope.of("creative"));

        final PunishmentEnd expired = this.punishment("bans", 6).end().orElseThrow();
        Assertions.assertThat(expired.kind()).isEqualTo(EndKind.EXPIRED);
        Assertions.assertThat(expired.at()).isEqualTo(millis(1704276000000L));
        Assertions.assertThat(expired.actor()).isEmpty();

        final PunishmentEnd revoked = this.punishment("bans", 7).end().orElseThrow();
        Assertions.assertThat(revoked.kind()).isEqualTo(EndKind.REVOKED);
        Assertions.assertThat(revoked.at()).isEqualTo(timestamp("2024-01-10T12:00:00"));
        Assertions.assertThat(revoked.actor().orElseThrow().operator()).isEqualTo(MOD_OPERATOR);
        Assertions.assertThat(revoked.reason()).contains("Appeal accepted");

        final PunishmentEnd byConsole = this.punishment("bans", 8).end().orElseThrow();
        Assertions.assertThat(byConsole.kind()).isEqualTo(EndKind.REVOKED);
        Assertions.assertThat(byConsole.actor().orElseThrow().operator())
                .isEqualTo(ConsoleOperator.INSTANCE);
        Assertions.assertThat(byConsole.reason()).isEmpty();

        final PunishmentEnd unknown = this.punishment("bans", 9).end().orElseThrow();
        Assertions.assertThat(unknown.kind()).isEqualTo(EndKind.REVOKED);
        Assertions.assertThat(unknown.at()).isEqualTo(timestamp("2024-01-20T12:00:00"));
        Assertions.assertThat(unknown.actor().orElseThrow().operator())
                .isEqualTo(CustomOperator.of("unknown", null));

        final Punishment longReason = this.punishment("bans", 12);
        Assertions.assertThat(longReason.reason()).hasSize(1024).startsWith("Spam Spam");
        Assertions.assertThat(longReason.scope()).isEqualTo(ServerScope.of("survival"));

        final Punishment mute = this.punishment("mutes", 1);
        Assertions.assertThat(mute.type()).isEqualTo(PunishmentType.MUTE);
        Assertions.assertThat(mute.end()).isEmpty();
        Assertions.assertThat(this.punishment("mutes", 2).end().orElseThrow().kind())
                .isEqualTo(EndKind.EXPIRED);

        final Punishment warning = this.punishment("warnings", 1);
        Assertions.assertThat(warning.type()).isEqualTo(PunishmentType.WARN);
        Assertions.assertThat(warning.expiresAt()).isEmpty();
        Assertions.assertThat(warning.end()).isEmpty();
        Assertions.assertThat(this.punishment("warnings", 2).end().orElseThrow().kind())
                .isEqualTo(EndKind.REVOKED);

        final Punishment kick = this.punishment("kicks", 1);
        Assertions.assertThat(kick.type()).isEqualTo(PunishmentType.KICK);
        Assertions.assertThat(kick.operator()).isEqualTo(ConsoleOperator.INSTANCE);
        Assertions.assertThat(kick.scope()).isEqualTo(ServerScope.of("kitpvp"));
        Assertions.assertThat(kick.expiresAt()).isEmpty();
        Assertions.assertThat(this.punishment("kicks", 2).operator()).isEqualTo(MOD_OPERATOR);

        Assertions.assertThat(this.count(SakuraTables.ACTIVE_PUNISHMENTS)).isEqualTo(8);

        final SakuraImportReport second = this.importFrom("litebans");
        Assertions.assertThat(second.imported()).isZero();
        Assertions.assertThat(second.skipped()).isEqualTo(16);
        Assertions.assertThat(this.count(SakuraTables.PUNISHMENTS)).isEqualTo(16);
    }

    @Test
    void importsHistoryAsPlayers() throws Exception {
        this.fill();

        this.importFrom("litebans");

        Assertions.assertThat(this.players.names(STEVE).join()).extracting(NameRecord::name)
                .containsExactly("Steve");
        final NameRecord steve = this.players.names(STEVE).join().getFirst();
        Assertions.assertThat(steve.firstSeen()).isEqualTo(timestamp("2024-01-01T10:00:00"));
        Assertions.assertThat(steve.lastSeen()).isEqualTo(timestamp("2024-02-01T10:00:00"));
        Assertions.assertThat(this.players.addresses(STEVE).join())
                .extracting(AddressRecord::address)
                .containsExactlyInAnyOrder(ip("203.0.113.5"), ip("203.0.113.6"),
                        ip("203.0.113.9"));
        Assertions.assertThat(this.players.names(ALEX).join()).extracting(NameRecord::name)
                .containsExactly("Alex");
        Assertions.assertThat(this.players.addresses(ALEX).join())
                .extracting(AddressRecord::address).containsExactly(ip("198.51.100.7"));
        Assertions.assertThat(this.players.names(HEROBRINE).join()).extracting(NameRecord::name)
                .containsExactly("Herobrine");
        Assertions.assertThat(this.players.addresses(HEROBRINE).join()).isEmpty();
    }

    @Test
    void dryRunsWriteNothing() {
        this.fill();

        final SakuraImportReport dry = this.importFrom("litebans", true);

        Assertions.assertThat(dry.dryRun()).isTrue();
        Assertions.assertThat(dry.imported()).isEqualTo(16);
        Assertions.assertThat(dry.failed()).isEqualTo(4);
        Assertions.assertThat(this.count(SakuraTables.PUNISHMENTS)).isZero();
        Assertions.assertThat(this.players.names(STEVE).join()).isEmpty();
        Assertions.assertThat(this.importFrom("litebans").imported()).isEqualTo(16);
    }

    @Test
    void wildcardsBecomeRangesWhereTheyCan() throws Exception {
        Assertions.assertThat(LiteBansSource.range("10.0.*.*"))
                .contains(AddressRangeVictim.of(ip("10.0.0.0"), 16));
        Assertions.assertThat(LiteBansSource.range("192.168.1.*"))
                .contains(AddressRangeVictim.of(ip("192.168.1.0"), 24));
        Assertions.assertThat(LiteBansSource.range("10.*"))
                .contains(AddressRangeVictim.of(ip("10.0.0.0"), 8));
        Assertions.assertThat(LiteBansSource.range("172.16.0.0/12"))
                .contains(AddressRangeVictim.of(ip("172.16.0.0"), 12));
        Assertions.assertThat(LiteBansSource.range("2001:db8::/32"))
                .contains(AddressRangeVictim.of(ip("2001:db8::"), 32));
        Assertions.assertThat(LiteBansSource.range("10.*.3.*")).isEmpty();
        Assertions.assertThat(LiteBansSource.range("*.*.*.*")).isEmpty();
        Assertions.assertThat(LiteBansSource.range("1.2.3.4")).isEmpty();
        Assertions.assertThat(LiteBansSource.range("10.0.0.0/40")).isEmpty();
        Assertions.assertThat(LiteBansSource.range("999.0.*.*")).isEmpty();
    }

    @Test
    void flagsAreReadFromEveryDriverType() {
        Assertions.assertThat(LiteBansSource.flag(Boolean.TRUE)).isTrue();
        Assertions.assertThat(LiteBansSource.flag(Boolean.FALSE)).isFalse();
        Assertions.assertThat(LiteBansSource.flag((byte) 1)).isTrue();
        Assertions.assertThat(LiteBansSource.flag(0L)).isFalse();
        Assertions.assertThat(LiteBansSource.flag(new byte[] {1})).isTrue();
        Assertions.assertThat(LiteBansSource.flag(new byte[] {0})).isFalse();
        Assertions.assertThat(LiteBansSource.flag("1")).isTrue();
        Assertions.assertThat(LiteBansSource.flag(null)).isFalse();
    }

    @Test
    void reportsFailuresWithTheirProblem() {
        this.fill();

        final List<ImportReport.Failure> failures = this.importFrom("litebans").failures();

        Assertions.assertThat(failures.toString()).contains("isn't a CIDR range",
                "isn't an address: .BedrockKid", "not a UUID: not-a-uuid",
                "not a UUID: garbage");
    }
}
