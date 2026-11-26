package net.serlith.sakurabans.core.importer;

import com.google.common.net.InetAddresses;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.serlith.sakurabans.api.model.AddressVictim;
import net.serlith.sakurabans.api.model.CategoryScope;
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
import net.serlith.sakurabans.core.model.SakuraImportReport;
import net.serlith.sakurabans.core.storage.dbo.SakuraTables;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

class LibertyBansSourceTest extends ImportTest {

    private static final UUID NAMELESS = UUID.fromString("3c1f0e2a-5b6d-4e7f-8a9b-0c1d2e3f4a5b");
    private static final String LONG_REASON = "Repeatedly joining on alternate accounts to"
            + " evade a ban on the main account. Evidence: matching address, matching client"
            + " brand, same chat patterns and the same build style on survival. Appeal on the"
            + " forum if you think this is a mistake, and name your main.";

    @Override
    protected ImportSources sources() {
        return new ImportSources(List.of(new LibertyBansSource(this.clock)));
    }

    private void load() {
        this.fixture(Engine.HSQLDB, "libertybans", "libertybans_")
                .script("libertybans/fixture.sql");
    }

    private Punishment imported(final int id) {
        final long stored = this.read(dsl -> dsl.select(SakuraTables.PUNISHMENTS.ID)
                .from(SakuraTables.PUNISHMENTS)
                .where(SakuraTables.PUNISHMENTS.EXTERNAL_ID.eq("libertybans:punishments:" + id))
                .fetchSingle().value1());
        return this.punishments.byId(stored).join().orElseThrow();
    }

    private static Instant seconds(final long epochSeconds) {
        return Instant.ofEpochSecond(epochSeconds);
    }

    @Test
    void surveyCountsPlayersAndHistory() {
        this.load();

        Assertions.assertThat(this.jobs().survey("libertybans").join()).isEqualTo(
                new ImportOutcome.Surveyed(Map.of("names", 5L, "addresses", 3L,
                        "history", 9L)));
    }

    @Test
    void importsPunishmentsWithTheirVictimsOperatorsScopesAndEnds() {
        this.load();

        final SakuraImportReport report = this.importFrom("libertybans");

        Assertions.assertThat(report.imported()).isEqualTo(8);
        Assertions.assertThat(report.failed()).isEqualTo(1);
        Assertions.assertThat(report.failures()).singleElement().satisfies(failure -> {
            Assertions.assertThat(failure.externalId()).contains("libertybans:punishments:9");
            Assertions.assertThat(failure.errors().getFirst().message())
                    .contains("Main Hub!");
        });
        Assertions.assertThat(report.players()).isEqualTo(8);
        Assertions.assertThat(report.truncated()).isZero();

        // Active permanent ban by the console; colour codes stripped.
        final Punishment ban = this.imported(1);
        Assertions.assertThat(ban.type()).isEqualTo(PunishmentType.BAN);
        Assertions.assertThat(ban.victim()).isInstanceOfSatisfying(PlayerVictim.class,
                victim -> Assertions.assertThat(victim.uuid()).isEqualTo(STEVE));
        Assertions.assertThat(ban.operator()).isInstanceOf(ConsoleOperator.class);
        Assertions.assertThat(ban.reason()).isEqualTo("Hacking badly");
        Assertions.assertThat(ban.createdAt()).isEqualTo(seconds(1704067200));
        Assertions.assertThat(ban.expiresAt()).isEmpty();
        Assertions.assertThat(ban.scope()).isEqualTo(GlobalScope.INSTANCE);
        Assertions.assertThat(ban.end()).isEmpty();

        // Active temporary mute of an address, by a player named from its latest name.
        final Punishment mute = this.imported(2);
        Assertions.assertThat(mute.victim()).isInstanceOfSatisfying(AddressVictim.class,
                victim -> Assertions.assertThat(victim.address())
                        .isEqualTo(InetAddresses.forString("203.0.113.9")));
        Assertions.assertThat(mute.operator()).isInstanceOfSatisfying(PlayerOperator.class,
                operator -> {
                    Assertions.assertThat(operator.uuid()).isEqualTo(MOD);
                    Assertions.assertThat(operator.name()).isEqualTo("Mod");
                });
        Assertions.assertThat(mute.expiresAt()).contains(seconds(1796083200));
        Assertions.assertThat(mute.scope()).isInstanceOfSatisfying(ServerScope.class,
                scope -> Assertions.assertThat(scope.server()).isEqualTo("lobby"));
        Assertions.assertThat(mute.end()).isEmpty();

        // Swept from the bans table after its end: expired then.
        final Punishment expired = this.imported(3);
        Assertions.assertThat(expired.end()).hasValueSatisfying(end -> {
            Assertions.assertThat(end.kind()).isEqualTo(EndKind.EXPIRED);
            Assertions.assertThat(end.at()).isEqualTo(seconds(1709251200));
        });

        // Gone from the bans table before any end: revoked, by nobody known, at its start.
        final Punishment revoked = this.imported(4);
        Assertions.assertThat(revoked.victim()).isInstanceOfSatisfying(CompositeVictim.class,
                victim -> {
                    Assertions.assertThat(victim.uuid()).isEqualTo(ALEX);
                    Assertions.assertThat(victim.address())
                            .isEqualTo(InetAddresses.forString("2001:db8::1"));
                });
        Assertions.assertThat(revoked.operator()).isInstanceOfSatisfying(CustomOperator.class,
                operator -> {
                    Assertions.assertThat(operator.uuid()).contains(NAMELESS);
                    Assertions.assertThat(operator.name()).isEqualTo(NAMELESS.toString());
                });
        Assertions.assertThat(revoked.reason()).isEqualTo(LONG_REASON).hasSize(256);
        Assertions.assertThat(revoked.scope()).isInstanceOfSatisfying(CategoryScope.class,
                scope -> Assertions.assertThat(scope.category()).isEqualTo("survival"));
        Assertions.assertThat(revoked.end()).hasValueSatisfying(end -> {
            Assertions.assertThat(end.kind()).isEqualTo(EndKind.REVOKED);
            Assertions.assertThat(end.at()).isEqualTo(seconds(1748736000));
            Assertions.assertThat(end.actor()).hasValueSatisfying(actor -> Assertions
                    .assertThat(actor.operator()).isInstanceOfSatisfying(CustomOperator.class,
                            operator -> {
                                Assertions.assertThat(operator.name()).isEqualTo("unknown");
                                Assertions.assertThat(operator.uuid()).isEmpty();
                            }));
        });
        final Punishment revokedMute = this.imported(5);
        Assertions.assertThat(revokedMute.expiresAt()).contains(seconds(1798761600));
        Assertions.assertThat(revokedMute.end()).map(PunishmentEnd::kind)
                .contains(EndKind.REVOKED);

        // Warns: one active, one past its end while still in the warns table.
        Assertions.assertThat(this.imported(6).end()).isEmpty();
        Assertions.assertThat(this.imported(6).type()).isEqualTo(PunishmentType.WARN);
        Assertions.assertThat(this.imported(7).end()).hasValueSatisfying(end -> {
            Assertions.assertThat(end.kind()).isEqualTo(EndKind.EXPIRED);
            Assertions.assertThat(end.at()).isEqualTo(seconds(1709251200));
        });

        final Punishment kick = this.imported(8);
        Assertions.assertThat(kick.type()).isEqualTo(PunishmentType.KICK);
        Assertions.assertThat(kick.victim()).isInstanceOf(PlayerVictim.class);
        Assertions.assertThat(kick.expiresAt()).isEmpty();
        Assertions.assertThat(kick.end()).isEmpty();

        Assertions.assertThat(this.count(SakuraTables.ACTIVE_PUNISHMENTS)).isEqualTo(3);
    }

    @Test
    void importsNamesBeforeAddresses() {
        this.load();

        this.importFrom("libertybans");

        Assertions.assertThat(this.players.names(STEVE).join()).extracting(NameRecord::name)
                .containsExactlyInAnyOrder("OldSteve", "Steve");
        Assertions.assertThat(this.players.profile(STEVE).join()).hasValueSatisfying(profile -> {
            Assertions.assertThat(profile.name()).isEqualTo("Steve");
            Assertions.assertThat(profile.firstSeen()).isEqualTo(seconds(1704067200));
            Assertions.assertThat(profile.lastSeen()).isEqualTo(seconds(1748736000));
            Assertions.assertThat(profile.lastAddress())
                    .contains(InetAddresses.forString("198.51.100.4"));
        });
        Assertions.assertThat(this.players.addresses(STEVE).join())
                .extracting(record -> record.address().getHostAddress())
                .containsExactlyInAnyOrder("203.0.113.7", "198.51.100.4");
        Assertions.assertThat(this.players.addresses(ALEX).join()).singleElement()
                .satisfies(record -> Assertions.assertThat(record.address())
                        .isEqualTo(InetAddresses.forString("2001:db8::1")));
        Assertions.assertThat(this.players.profile(MOD).join()).hasValueSatisfying(profile ->
                Assertions.assertThat(profile.name()).isEqualTo("Mod"));
    }

    @Test
    void dryRunsWriteNothingAndRepeatsImportNothing() {
        this.load();

        final SakuraImportReport dry = this.importFrom("libertybans", true);
        Assertions.assertThat(dry.dryRun()).isTrue();
        Assertions.assertThat(dry.imported()).isEqualTo(8);
        Assertions.assertThat(dry.failed()).isEqualTo(1);
        Assertions.assertThat(this.count(SakuraTables.PUNISHMENTS)).isZero();
        Assertions.assertThat(this.count(SakuraTables.PLAYERS)).isZero();

        Assertions.assertThat(this.importFrom("libertybans").imported()).isEqualTo(8);
        final SakuraImportReport second = this.importFrom("libertybans");
        Assertions.assertThat(second.imported()).isZero();
        Assertions.assertThat(second.skipped()).isEqualTo(8);
        Assertions.assertThat(this.count(SakuraTables.PUNISHMENTS)).isEqualTo(8);
    }

    @Test
    void shipsWithTheStandardSources() {
        Assertions.assertThat(ImportSources.standard().source("libertybans"))
                .hasValueSatisfying(source -> Assertions.assertThat(source.databaseSection())
                        .contains("libertybans"));
    }
}
