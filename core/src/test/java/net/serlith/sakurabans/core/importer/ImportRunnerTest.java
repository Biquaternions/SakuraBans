package net.serlith.sakurabans.core.importer;

import java.net.InetAddress;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.IntStream;
import net.serlith.sakurabans.api.audit.AuditAction;
import net.serlith.sakurabans.api.imports.ImportOptions;
import net.serlith.sakurabans.api.imports.ImportedPunishment;
import net.serlith.sakurabans.api.model.EndKind;
import net.serlith.sakurabans.api.model.ErasedAddressVictim;
import net.serlith.sakurabans.api.model.Lengths;
import net.serlith.sakurabans.api.model.PlayerOperator;
import net.serlith.sakurabans.api.model.PlayerVictim;
import net.serlith.sakurabans.api.model.PunishmentType;
import net.serlith.sakurabans.api.model.ServerScope;
import net.serlith.sakurabans.api.punish.Enforcement;
import net.serlith.sakurabans.core.fake.FakeOnlinePlayer;
import net.serlith.sakurabans.core.model.ImportedPlayer;
import net.serlith.sakurabans.core.model.SakuraImportReport;
import net.serlith.sakurabans.core.storage.dbo.SakuraTables;
import net.serlith.sakurabans.core.storage.dbo.records.SbPunishmentsRecord;
import net.serlith.sakurabans.core.sync.SyncPacket;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

/** The import runner (plan S38): batches, dry runs, re-runs, failures, ends, supersession. */
class ImportRunnerTest extends ImportTest {

    private final FakeSource source = new FakeSource();

    @Override
    protected ImportSources sources() {
        return new ImportSources(List.of(this.source));
    }

    private Instant ago(final Duration duration) {
        return this.now.get().minus(duration);
    }

    private ImportedPunishment.Builder ban(final UUID victim, final Instant at, final String id) {
        return ImportedPunishment.builder(PunishmentType.BAN, PlayerVictim.of(victim),
                        PlayerOperator.of(MOD, "Mod"), at)
                .reason("cheating")
                .externalId(FakeSource.ID + ":bans:" + id);
    }

    private void add(final ImportedPunishment.Builder punishment) {
        final ImportedPunishment built = punishment.build();
        this.source.row(reader -> reader.punishment(built));
    }

    private SbPunishmentsRecord row(final String id) {
        return this.read(dsl -> dsl.selectFrom(SakuraTables.PUNISHMENTS)
                .where(SakuraTables.PUNISHMENTS.EXTERNAL_ID.eq(FakeSource.ID + ":bans:" + id))
                .fetchSingle());
    }

    private boolean active(final String id) {
        final long punishmentId = this.row(id).id();
        return this.count(SakuraTables.ACTIVE_PUNISHMENTS,
                SakuraTables.ACTIVE_PUNISHMENTS.PUNISHMENT_ID.eq(punishmentId)) == 1;
    }

    @Test
    void batchesAreStoredOneByOneAndRepeatsImportNothing() {
        final List<ImportedPunishment> punishments = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            punishments.add(this.ban(UUID.randomUUID(), this.ago(Duration.ofDays(1)), "b" + i)
                    .build());
        }
        final ImportOptions options = ImportOptions.builder().batchSize(2).source("import:test")
                .build();

        final ImportOutcome first = this.jobs().run(punishments, options, CONSOLE).join();

        Assertions.assertThat(first).isInstanceOfSatisfying(ImportOutcome.Done.class, done -> {
            Assertions.assertThat(done.report().imported()).isEqualTo(5);
            Assertions.assertThat(done.report().skipped()).isZero();
        });
        Assertions.assertThat(this.log.entries("info").stream()
                        .filter(entry -> String.valueOf(entry.arguments()[0])
                                .startsWith("Import from {}: batch")))
                .hasSize(3);
        Assertions.assertThat(this.count(SakuraTables.PUNISHMENTS)).isEqualTo(5);
        Assertions.assertThat(this.count(SakuraTables.PUNISHMENTS,
                SakuraTables.PUNISHMENTS.SOURCE.eq("import:test"))).isEqualTo(5);

        final ImportOutcome second = this.jobs().run(punishments, options, CONSOLE).join();

        Assertions.assertThat(second).isInstanceOfSatisfying(ImportOutcome.Done.class, done -> {
            Assertions.assertThat(done.report().imported()).isZero();
            Assertions.assertThat(done.report().skipped()).isEqualTo(5);
        });
        Assertions.assertThat(this.count(SakuraTables.PUNISHMENTS)).isEqualTo(5);
        Assertions.assertThat(this.count(SakuraTables.AUDIT_LOG,
                SakuraTables.AUDIT_LOG.ACTION.eq(AuditAction.IMPORT))).isEqualTo(2);
    }

    @Test
    void dryRunsReadAndCountButStoreNothing() {
        this.add(this.ban(STEVE, this.ago(Duration.ofDays(1)), "1"));
        this.add(this.ban(ALEX, this.ago(Duration.ofDays(1)), "2"));
        this.source.row(reader -> reader.player(ImportedPlayer.named(STEVE, "Steve",
                this.ago(Duration.ofDays(2)))));

        final SakuraImportReport report = this.importFrom(FakeSource.ID, true);

        Assertions.assertThat(report.dryRun()).isTrue();
        Assertions.assertThat(report.imported()).isEqualTo(2);
        Assertions.assertThat(report.players()).isEqualTo(1);
        Assertions.assertThat(this.count(SakuraTables.PUNISHMENTS)).isZero();
        Assertions.assertThat(this.count(SakuraTables.PLAYERS)).isZero();
        Assertions.assertThat(this.count(SakuraTables.AUDIT_LOG)).isZero();
        Assertions.assertThat(this.published).isEmpty();

        this.importFrom(FakeSource.ID);
        Assertions.assertThat(this.importFrom(FakeSource.ID, true).skipped()).isEqualTo(2);
    }

    @Test
    void badRowsAreCountedAndTheFirstFiftyListed() {
        IntStream.range(0, 60).forEach(i -> this.source.row(reader ->
                reader.failure("bans", Integer.toString(i), "no victim")));
        this.add(ImportedPunishment.builder(PunishmentType.BAN,
                        ErasedAddressVictim.of("ab".repeat(32), null),
                        PlayerOperator.of(MOD, "Mod"), this.ago(Duration.ofDays(1)))
                .externalId("fake:bans:erased"));
        this.add(this.ban(STEVE, this.ago(Duration.ofDays(1)), "good"));

        final SakuraImportReport report = this.importFrom(FakeSource.ID);

        Assertions.assertThat(report.imported()).isEqualTo(1);
        Assertions.assertThat(report.failed()).isEqualTo(61);
        Assertions.assertThat(report.failures()).hasSize(50);
        Assertions.assertThat(report.failures().getFirst().externalId())
                .contains("fake:bans:0");
        Assertions.assertThat(report.failures().getFirst().errors().getFirst().message())
                .isEqualTo("no victim");
    }

    @Test
    void endedRowsStayEndedAndTheOlderOfTwoActiveBansIsSuperseded() {
        this.add(this.ban(STEVE, this.ago(Duration.ofDays(10)), "older"));
        this.add(this.ban(STEVE, this.ago(Duration.ofDays(5)), "newer"));
        // Older than the one in the slot by now: it is the one replaced.
        this.add(this.ban(STEVE, this.ago(Duration.ofDays(20)), "oldest"));
        this.add(this.ban(ALEX, this.ago(Duration.ofDays(10)), "expired")
                .expiresAt(this.ago(Duration.ofDays(3))));
        this.add(this.ban(UUID.randomUUID(), this.ago(Duration.ofDays(10)), "revoked")
                .ended(EndKind.REVOKED, this.ago(Duration.ofDays(9)),
                        PlayerOperator.of(MOD, "Mod"), "appeal accepted"));
        this.add(this.ban(UUID.randomUUID(), this.ago(Duration.ofDays(10)), "lifted")
                .ended(EndKind.REVOKED, this.ago(Duration.ofDays(9)), null, null));

        final SakuraImportReport report = this.importFrom(FakeSource.ID);

        Assertions.assertThat(report.imported()).isEqualTo(6);
        Assertions.assertThat(report.superseded()).isEqualTo(2);
        Assertions.assertThat(this.row("older").endKind()).isEqualTo(EndKind.SUPERSEDED);
        Assertions.assertThat(this.row("older").endedAt())
                .isEqualTo(this.ago(Duration.ofDays(5)));
        Assertions.assertThat(this.row("oldest").endKind()).isEqualTo(EndKind.SUPERSEDED);
        Assertions.assertThat(this.row("oldest").endedAt())
                .isEqualTo(this.ago(Duration.ofDays(5)));
        Assertions.assertThat(this.active("newer")).isTrue();
        Assertions.assertThat(this.active("older")).isFalse();
        Assertions.assertThat(this.row("expired").endKind()).isEqualTo(EndKind.EXPIRED);
        Assertions.assertThat(this.row("expired").endedAt())
                .isEqualTo(this.ago(Duration.ofDays(3)));
        Assertions.assertThat(this.active("expired")).isFalse();
        final SbPunishmentsRecord revoked = this.row("revoked");
        Assertions.assertThat(revoked.endKind()).isEqualTo(EndKind.REVOKED);
        Assertions.assertThat(revoked.revokerUuid()).isEqualTo(MOD);
        Assertions.assertThat(revoked.revokeReason()).isEqualTo("appeal accepted");
        Assertions.assertThat(revoked.revokerSource()).isEqualTo("import:fake");
        Assertions.assertThat(this.row("lifted").endKind()).isEqualTo(EndKind.REVOKED);
        // Nobody is named: the unknown revoker stands in, so the row can be read back.
        Assertions.assertThat(this.row("lifted").revokerName()).isEqualTo("unknown");
        Assertions.assertThat(this.count(SakuraTables.ACTIVE_PUNISHMENTS)).isEqualTo(1);
    }

    @Test
    void reasonsLoseTheirColourCodes() {
        this.add(this.ban(STEVE, this.ago(Duration.ofDays(1)), "colours")
                .reason("&cHacking §lhard&r &#ff00ffnow §x§a§b§c§d§e§f!")
                .ended(EndKind.REVOKED, this.ago(Duration.ofHours(1)), null, "&aforgiven"));

        this.importFrom(FakeSource.ID);

        Assertions.assertThat(this.row("colours").reason()).isEqualTo("Hacking hard now !");
        Assertions.assertThat(this.row("colours").revokeReason()).isEqualTo("forgiven");
    }

    @Test
    void longTextsAreShortenedAndCounted() {
        this.source.row(reader -> reader.punishment(this.ban(STEVE,
                        this.ago(Duration.ofDays(1)), "long")
                .reason(reader.reason("x".repeat(2_000)))
                .build()));

        final SakuraImportReport report = this.importFrom(FakeSource.ID);

        Assertions.assertThat(report.truncated()).isEqualTo(1);
        Assertions.assertThat(this.row("long").reason()).hasSize(Lengths.MAX_REASON);
    }

    @Test
    void unknownKindsFallBackAndUnknownScopesAreCreated() {
        this.add(this.ban(STEVE, this.ago(Duration.ofDays(1)), "kind").kind("made-up"));
        this.add(this.ban(ALEX, this.ago(Duration.ofDays(1)), "wrong-type").kind("mute"));
        this.add(this.ban(MOD, this.ago(Duration.ofDays(1)), "scoped")
                .kind("ip-ban").scope(ServerScope.of("survival")));

        Assertions.assertThat(this.importFrom(FakeSource.ID).failed()).isZero();

        Assertions.assertThat(this.row("kind").kind()).isEqualTo("ban");
        Assertions.assertThat(this.row("wrong-type").kind()).isEqualTo("ban");
        Assertions.assertThat(this.row("scoped").kind()).isEqualTo("ip-ban");
        Assertions.assertThat(this.count(SakuraTables.SCOPES,
                SakuraTables.SCOPES.VALUE.eq("survival"))).isEqualTo(1);
    }

    @Test
    void playersMergeKeepingTheNewestNameAndTheWidestTimes() throws Exception {
        final InetAddress address = InetAddress.getByName("198.51.100.4");
        this.players.recordConnection(STEVE, "Steve", this.ago(Duration.ofDays(1)), null,
                null).join();
        this.source.row(reader -> reader.player(new ImportedPlayer(STEVE, "OldSteve", null,
                this.ago(Duration.ofDays(400)), this.ago(Duration.ofDays(300)))));
        this.source.row(reader -> reader.player(ImportedPlayer.at(STEVE, address,
                this.ago(Duration.ofDays(350)))));
        this.source.row(reader -> reader.player(ImportedPlayer.at(ALEX, address,
                this.ago(Duration.ofDays(350)))));

        final SakuraImportReport report = this.importFrom(FakeSource.ID);

        Assertions.assertThat(report.players()).isEqualTo(2);
        Assertions.assertThat(report.failed()).isEqualTo(1);
        Assertions.assertThat(report.failures().getFirst().externalId())
                .contains("player:" + ALEX);
        Assertions.assertThat(this.players.profile(STEVE).join()).hasValueSatisfying(profile -> {
            Assertions.assertThat(profile.name()).isEqualTo("Steve");
            Assertions.assertThat(profile.firstSeen()).isEqualTo(this.ago(Duration.ofDays(400)));
            Assertions.assertThat(profile.lastSeen()).isEqualTo(this.ago(Duration.ofDays(1)));
        });
        Assertions.assertThat(this.players.names(STEVE).join())
                .extracting(name -> name.name()).containsExactly("Steve", "OldSteve");
        Assertions.assertThat(this.players.addresses(STEVE).join()).singleElement()
                .satisfies(known -> Assertions.assertThat(known.address()).isEqualTo(address));
        Assertions.assertThat(this.players.profile(ALEX).join()).isEmpty();
    }

    @Test
    void activeBansTakeEffectEverywhereAfterTheImport() {
        final FakeOnlinePlayer steve = this.platform.join(STEVE, "Steve", this.home);
        this.add(this.ban(STEVE, this.ago(Duration.ofDays(1)), "online"));

        this.importFrom(FakeSource.ID);

        Assertions.assertThat(steve.disconnectReason()).isPresent();
        Assertions.assertThat(this.published)
                .containsExactly(new SyncPacket.ImportedPacket(true));
    }

    @Test
    void withoutEnforcementNobodyIsKicked() {
        final FakeOnlinePlayer steve = this.platform.join(STEVE, "Steve", this.home);
        final ImportOptions options = ImportOptions.builder().enforcement(Enforcement.NONE)
                .build();

        this.jobs().run(List.of(this.ban(STEVE, this.ago(Duration.ofDays(1)), "quiet")
                .build()), options, CONSOLE).join();

        Assertions.assertThat(steve.disconnectReason()).isEmpty();
        Assertions.assertThat(this.published)
                .containsExactly(new SyncPacket.ImportedPacket(false));
    }
}
