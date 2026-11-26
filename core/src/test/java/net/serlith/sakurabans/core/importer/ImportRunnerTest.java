package net.serlith.sakurabans.core.importer;

import java.net.InetAddress;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.stream.IntStream;
import net.serlith.sakurabans.api.model.ConsoleOperator;
import net.serlith.sakurabans.api.model.EndKind;
import net.serlith.sakurabans.api.model.ErasedAddressVictim;
import net.serlith.sakurabans.api.model.GlobalScope;
import net.serlith.sakurabans.api.model.Lengths;
import net.serlith.sakurabans.api.model.PlayerOperator;
import net.serlith.sakurabans.api.model.PlayerVictim;
import net.serlith.sakurabans.api.model.PunishmentType;
import net.serlith.sakurabans.api.model.ServerScope;
import net.serlith.sakurabans.api.model.Visibility;
import net.serlith.sakurabans.core.fake.FakeOnlinePlayer;
import net.serlith.sakurabans.core.model.AuditAction;
import net.serlith.sakurabans.core.model.ImportedPlayer;
import net.serlith.sakurabans.core.storage.dbo.SakuraTables;
import net.serlith.sakurabans.core.storage.dbo.records.SbPunishmentsRecord;
import net.serlith.sakurabans.core.sync.SyncPacket;
import org.assertj.core.api.Assertions;
import org.jspecify.annotations.Nullable;
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

    private ImportedPunishment ban(final UUID victim, final Instant at, final String id) {
        return this.ban(victim, at, id, null, null);
    }

    private ImportedPunishment ban(final UUID victim, final Instant at, final String id,
            final @Nullable Instant expiresAt, final ImportedPunishment.@Nullable End end) {
        return new ImportedPunishment(PunishmentType.BAN, PlayerVictim.of(victim),
                PlayerOperator.of(MOD, "Mod"), at, expiresAt, GlobalScope.INSTANCE, "cheating",
                Visibility.NORMAL, false, null, end, FakeSource.ID + ":bans:" + id);
    }

    private void add(final ImportedPunishment punishment) {
        this.source.row(reader -> reader.punishment(punishment));
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
    void repeatsImportNothing() {
        for (int i = 0; i < 5; i++) {
            this.add(this.ban(UUID.randomUUID(), this.ago(Duration.ofDays(1)), "b" + i));
        }

        final ImportReport first = this.importFrom(FakeSource.ID);

        Assertions.assertThat(first.imported()).isEqualTo(5);
        Assertions.assertThat(first.skipped()).isZero();
        Assertions.assertThat(this.count(SakuraTables.PUNISHMENTS)).isEqualTo(5);
        Assertions.assertThat(this.count(SakuraTables.PUNISHMENTS,
                SakuraTables.PUNISHMENTS.SOURCE.eq("import:fake"))).isEqualTo(5);

        final ImportReport second = this.importFrom(FakeSource.ID);

        Assertions.assertThat(second.imported()).isZero();
        Assertions.assertThat(second.skipped()).isEqualTo(5);
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

        final ImportReport report = this.importFrom(FakeSource.ID, true);

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
        this.add(new ImportedPunishment(PunishmentType.BAN,
                ErasedAddressVictim.of("ab".repeat(32), null), PlayerOperator.of(MOD, "Mod"),
                this.ago(Duration.ofDays(1)), null, GlobalScope.INSTANCE, "",
                Visibility.NORMAL, false, null, null, "fake:bans:erased"));
        this.add(this.ban(STEVE, this.ago(Duration.ofDays(1)), "good"));

        final ImportReport report = this.importFrom(FakeSource.ID);

        Assertions.assertThat(report.imported()).isEqualTo(1);
        Assertions.assertThat(report.failed()).isEqualTo(61);
        Assertions.assertThat(report.failures()).hasSize(50);
        Assertions.assertThat(report.failures().getFirst().externalId())
                .isEqualTo("fake:bans:0");
        Assertions.assertThat(report.failures().getFirst().errors().getFirst().message())
                .isEqualTo("no victim");
    }

    @Test
    void endedRowsStayEndedAndTheOlderOfTwoActiveBansIsSuperseded() {
        this.add(this.ban(STEVE, this.ago(Duration.ofDays(10)), "older"));
        this.add(this.ban(STEVE, this.ago(Duration.ofDays(5)), "newer"));
        // Older than the one in the slot by now: it is the one replaced.
        this.add(this.ban(STEVE, this.ago(Duration.ofDays(20)), "oldest"));
        this.add(this.ban(ALEX, this.ago(Duration.ofDays(10)), "expired",
                this.ago(Duration.ofDays(3)), null));
        this.add(this.ban(UUID.randomUUID(), this.ago(Duration.ofDays(10)), "revoked", null,
                new ImportedPunishment.End(EndKind.REVOKED, this.ago(Duration.ofDays(9)),
                        PlayerOperator.of(MOD, "Mod"), "appeal accepted")));
        this.add(this.ban(UUID.randomUUID(), this.ago(Duration.ofDays(10)), "lifted", null,
                new ImportedPunishment.End(EndKind.REVOKED, this.ago(Duration.ofDays(9)), null,
                        null)));

        final ImportReport report = this.importFrom(FakeSource.ID);

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
        this.add(new ImportedPunishment(PunishmentType.BAN, PlayerVictim.of(STEVE),
                PlayerOperator.of(MOD, "Mod"), this.ago(Duration.ofDays(1)), null,
                GlobalScope.INSTANCE, "&cHacking §lhard&r &#ff00ffnow §x§a§b§c§d§e§f!",
                Visibility.NORMAL, false, null, new ImportedPunishment.End(EndKind.REVOKED,
                        this.ago(Duration.ofHours(1)), null, "&aforgiven"),
                FakeSource.ID + ":bans:colours"));

        this.importFrom(FakeSource.ID);

        Assertions.assertThat(this.row("colours").reason()).isEqualTo("Hacking hard now !");
        Assertions.assertThat(this.row("colours").revokeReason()).isEqualTo("forgiven");
    }

    @Test
    void longTextsAreShortenedAndCounted() {
        this.source.row(reader -> reader.punishment(new ImportedPunishment(PunishmentType.BAN,
                PlayerVictim.of(STEVE), PlayerOperator.of(MOD, "Mod"),
                this.ago(Duration.ofDays(1)), null, GlobalScope.INSTANCE,
                reader.reason("x".repeat(2_000)), Visibility.NORMAL, false, null, null,
                FakeSource.ID + ":bans:long")));

        final ImportReport report = this.importFrom(FakeSource.ID);

        Assertions.assertThat(report.truncated()).isEqualTo(1);
        Assertions.assertThat(this.row("long").reason()).hasSize(Lengths.MAX_REASON);
    }

    @Test
    void shadowMutesAreKeptAndUnknownScopesAreCreated() {
        this.add(this.ban(STEVE, this.ago(Duration.ofDays(1)), "plain"));
        this.add(new ImportedPunishment(PunishmentType.BAN, PlayerVictim.of(MOD),
                ConsoleOperator.INSTANCE, this.ago(Duration.ofDays(1)), null,
                ServerScope.of("survival"), "", Visibility.NORMAL, false, null, null,
                FakeSource.ID + ":bans:scoped"));
        this.add(new ImportedPunishment(PunishmentType.MUTE, PlayerVictim.of(ALEX),
                ConsoleOperator.INSTANCE, this.ago(Duration.ofDays(1)), null,
                GlobalScope.INSTANCE, "", Visibility.NORMAL, true, null, null,
                FakeSource.ID + ":bans:quiet"));
        this.add(new ImportedPunishment(PunishmentType.BAN, PlayerVictim.of(ALEX),
                ConsoleOperator.INSTANCE, this.ago(Duration.ofDays(1)), null,
                GlobalScope.INSTANCE, "", Visibility.NORMAL, true, null, null,
                FakeSource.ID + ":bans:loud"));

        final ImportReport report = this.importFrom(FakeSource.ID);

        Assertions.assertThat(report.failed()).isEqualTo(1);
        Assertions.assertThat(report.failures().getFirst().errors().getFirst().path())
                .isEqualTo("shadow");
        Assertions.assertThat(this.row("plain").shadow()).isFalse();
        Assertions.assertThat(this.row("quiet").shadow()).isTrue();
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

        final ImportReport report = this.importFrom(FakeSource.ID);

        Assertions.assertThat(report.players()).isEqualTo(2);
        Assertions.assertThat(report.failed()).isEqualTo(1);
        Assertions.assertThat(report.failures().getFirst().externalId())
                .isEqualTo("player:" + ALEX);
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
                .containsExactly(new SyncPacket.ImportedPacket());
    }
}
