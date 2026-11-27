package net.serlith.sakurabans.core.punish;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import net.serlith.sakurabans.api.model.EndKind;
import net.serlith.sakurabans.api.model.PlayerVictim;
import net.serlith.sakurabans.api.model.Punishment;
import net.serlith.sakurabans.api.model.PunishmentType;
import net.serlith.sakurabans.api.punish.DraftPunishment;
import net.serlith.sakurabans.api.punish.PunishOptions;
import net.serlith.sakurabans.api.punish.PunishResult;
import net.serlith.sakurabans.api.punish.RevocationRequest;
import net.serlith.sakurabans.api.punish.RevocationTarget;
import net.serlith.sakurabans.core.ServiceTest;
import net.serlith.sakurabans.core.enforce.SyncReceiver;
import net.serlith.sakurabans.core.message.NetworkTotals;
import net.serlith.sakurabans.core.model.LoginAttempt;
import net.serlith.sakurabans.core.model.PlayerCounts;
import net.serlith.sakurabans.core.model.PunishmentTotals;
import net.serlith.sakurabans.core.sync.SyncPacket;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

/** The counts behind the message tags and placeholders. */
class PunishmentCountsTest extends ServiceTest {

    private Punishment punish(final PunishmentType type, final boolean enforce,
            final DraftPunishment.Builder draft) {
        return ((PunishResult.Success) this.service.punish(draft.build(),
                PunishOptions.builder().enforce(enforce).build()).join()).punishment();
    }

    private Punishment punish(final PunishmentType type) {
        return this.punish(type, true, DraftPunishment.builder(type, PlayerVictim.of(STEVE),
                MODERATOR));
    }

    private Punishment punish(final PunishmentType type, final Duration duration) {
        return this.punish(type, true, DraftPunishment.builder(type, PlayerVictim.of(STEVE),
                MODERATOR).duration(duration));
    }

    private void login() {
        this.loginGate.check(new LoginAttempt(STEVE, "Steve", this.home, null)).join();
        eventually(() -> this.counts.cached(STEVE).isPresent());
        this.platform.join(STEVE, "Steve", this.home);
    }

    private PlayerCounts steve() {
        return this.counts.cached(STEVE).orElseThrow();
    }

    // Counts whose reads answer only when the test completes them, in the order they started.
    private PunishmentCounts controlled(final List<CompletableFuture<PunishmentTotals>> totals,
            final List<CompletableFuture<PlayerCounts>> players) {
        return new PunishmentCounts(new PunishmentCounts.Reads() {
            @Override
            public CompletableFuture<PunishmentTotals> totals(final Instant now) {
                final CompletableFuture<PunishmentTotals> answer = new CompletableFuture<>();
                totals.add(answer);
                return answer;
            }

            @Override
            public CompletableFuture<PlayerCounts> countsOf(final UUID uuid, final Instant now) {
                final CompletableFuture<PlayerCounts> answer = new CompletableFuture<>();
                players.add(answer);
                return answer;
            }
        }, new NetworkTotals(), this.bus, this.config, this.executors, this.clock,
                this.log.logger());
    }

    private static PlayerCounts bans(final long bans) {
        return new PlayerCounts(bans, 0, 0, 0, 0, 0, List.of());
    }

    @Test
    void totalsAreAbsentUntilReadAndLeaveOutVoidedAndExpiredOnes() {
        Assertions.assertThat(this.counts.totals()).isEmpty();
        this.punish(PunishmentType.BAN);
        this.punish(PunishmentType.MUTE, Duration.ofMinutes(5));
        this.punish(PunishmentType.WARN);
        final Punishment voided = this.punish(PunishmentType.WARN);
        this.service.voidPunishment(voided.id(), MODERATOR, "mistake").join();
        this.punish(PunishmentType.KICK, false,
                DraftPunishment.builder(PunishmentType.KICK, PlayerVictim.of(ALEX), MODERATOR));

        this.counts.refreshTotals().join();
        Assertions.assertThat(this.counts.totals())
                .contains(new PunishmentTotals(1, 1, 1, 1, 1, 1, 1));

        // Expired but not yet swept: no longer active, still in the history.
        this.now.set(this.now.get().plus(Duration.ofMinutes(5)));
        this.counts.refreshTotals().join();
        Assertions.assertThat(this.counts.totals())
                .contains(new PunishmentTotals(1, 0, 1, 1, 1, 1, 1));
    }

    @Test
    void totalsAreReadAgainOnlyAfterTheInterval() {
        this.counts.refreshIfDue().join();
        Assertions.assertThat(this.counts.totals().orElseThrow().totalBans()).isZero();
        this.punish(PunishmentType.BAN);

        this.now.set(this.now.get().plus(Duration.ofMinutes(1)));
        this.counts.refreshIfDue().join();
        Assertions.assertThat(this.counts.totals().orElseThrow().totalBans()).isZero();

        this.now.set(this.now.get().plus(Duration.ofMinutes(1)));
        this.counts.refreshIfDue().join();
        Assertions.assertThat(this.counts.totals().orElseThrow().totalBans()).isOne();
    }

    @Test
    void playerCountsLoadAtLoginAndFollowTheirPunishments() {
        this.punish(PunishmentType.WARN, false,
                DraftPunishment.builder(PunishmentType.WARN, PlayerVictim.of(STEVE), MODERATOR));
        Assertions.assertThat(this.counts.cached(STEVE)).as("absent before the load").isEmpty();

        this.login();
        Assertions.assertThat(this.steve().warns()).isOne();
        Assertions.assertThat(this.steve().activeWarns(this.now.get())).isOne();

        final Punishment mute = this.punish(PunishmentType.MUTE);
        Assertions.assertThat(this.steve().mutes()).isOne();
        this.service.revoke(RevocationRequest.builder(RevocationTarget.ById.of(mute.id()),
                MODERATOR).build()).join();
        Assertions.assertThat(this.steve().mutes()).as("revoked: still received").isOne();

        final Punishment warn = this.punish(PunishmentType.WARN, Duration.ofMinutes(10));
        Assertions.assertThat(this.steve().warns()).isEqualTo(2);
        Assertions.assertThat(this.steve().activeWarns(this.now.get())).isEqualTo(2);

        this.service.voidPunishment(warn.id(), MODERATOR, "wrong player").join();
        Assertions.assertThat(this.steve().warns()).as("voided").isOne();
        Assertions.assertThat(this.steve().activeWarns(this.now.get())).isOne();

        this.punish(PunishmentType.KICK);
        Assertions.assertThat(this.steve().kicks()).isOne();
    }

    @Test
    void activeWarningThatExpiresStopsCountingWithoutReload() {
        this.login();
        this.punish(PunishmentType.WARN, Duration.ofMinutes(10));
        Assertions.assertThat(this.steve().activeWarns(this.now.get())).isOne();

        this.now.set(this.now.get().plus(Duration.ofMinutes(10)));
        Assertions.assertThat(this.steve().activeWarns(this.now.get())).isZero();

        // The sweeper ends it: read again, same answer.
        this.sweeper.sweep().join();
        eventually(() -> this.steve().activeWarnExpiries().isEmpty());
        Assertions.assertThat(this.steve().warns()).isOne();
    }

    @Test
    void otherInstancesPunishmentsArriveThroughSync() {
        final SyncReceiver receiver = this.syncReceiver();
        this.login();

        final Punishment ban = this.punish(PunishmentType.BAN, false,
                DraftPunishment.builder(PunishmentType.BAN, PlayerVictim.of(STEVE), MODERATOR));
        Assertions.assertThat(this.steve().bans()).as("not told yet").isZero();
        receiver.handle(SyncPacket.PunishmentEnforcePacket.of(ban.id())).join();
        Assertions.assertThat(this.steve().bans()).isOne();

        // Voided elsewhere: the row changed, this instance hears of it.
        this.punishments.voidPunishment(ban.id(), MODERATOR, null, this.now.get()).join();
        receiver.handle(new SyncPacket.PunishmentsEndedPacket(List.of(ban.id()))).join();
        Assertions.assertThat(this.steve().bans()).isZero();

        final Punishment mute = this.punish(PunishmentType.MUTE, false,
                DraftPunishment.builder(PunishmentType.MUTE, PlayerVictim.of(STEVE), MODERATOR));
        receiver.handle(new SyncPacket.PunishmentsResetPacket()).join();
        Assertions.assertThat(this.steve().mutes()).as("a reset reloads everyone").isOne();
        this.punishments.end(mute.id(), EndKind.REVOKED, this.now.get(), MODERATOR, null).join();
        receiver.handle(new SyncPacket.PunishmentRevokePacket(mute.id(),
                net.serlith.sakurabans.api.model.Visibility.NORMAL)).join();
        Assertions.assertThat(this.steve().mutes()).isOne();
    }

    @Test
    void droppedAtQuitAndLateAnswersDontBringThemBack() {
        this.login();
        this.loginGate.quit(STEVE);
        Assertions.assertThat(this.counts.cached(STEVE)).isEmpty();

        // A load still running when they leave fills nothing.
        final var load = this.counts.load(STEVE);
        this.counts.forget(STEVE);
        load.join();
        Assertions.assertThat(this.counts.cached(STEVE)).isEmpty();
        this.counts.reloadAll().join();
        Assertions.assertThat(this.counts.cached(STEVE)).isEmpty();
    }

    @Test
    void olderAnswerArrivingLateDoesntOverwriteNewerOne() {
        final List<CompletableFuture<PlayerCounts>> answers = new CopyOnWriteArrayList<>();
        final PunishmentCounts counts = this.controlled(new CopyOnWriteArrayList<>(), answers);
        final CompletableFuture<Void> older = counts.load(STEVE);
        final CompletableFuture<Void> newer = counts.reload(STEVE);
        Assertions.assertThat(answers).hasSize(2);

        answers.get(1).complete(bans(2));
        newer.join();
        answers.get(0).complete(bans(1));
        older.join();

        Assertions.assertThat(counts.cached(STEVE)).contains(bans(2));
        // The next change is read again and answers as usual.
        final CompletableFuture<Void> next = counts.reload(STEVE);
        answers.get(2).complete(bans(3));
        next.join();
        Assertions.assertThat(counts.cached(STEVE)).contains(bans(3));
    }

    @Test
    void failedTotalsReadIsTriedAgainOnTheNextTick() {
        final List<CompletableFuture<PunishmentTotals>> reads = new CopyOnWriteArrayList<>();
        final PunishmentCounts counts = this.controlled(reads, new CopyOnWriteArrayList<>());
        final CompletableFuture<Void> failing = counts.refreshIfDue();
        Assertions.assertThat(counts.refreshIfDue()).as("no second read while one runs")
                .isDone();
        Assertions.assertThat(reads).hasSize(1);

        reads.get(0).completeExceptionally(new IllegalStateException("database down"));
        Assertions.assertThat(failing).isCompletedExceptionally();

        final CompletableFuture<Void> retried = counts.refreshIfDue();
        Assertions.assertThat(reads).as("tried again at once").hasSize(2);
        final PunishmentTotals read = new PunishmentTotals(1, 0, 0, 1, 0, 0, 0);
        reads.get(1).complete(read);
        retried.join();
        Assertions.assertThat(counts.totals()).contains(read);

        // A read that answered waits for the interval.
        counts.refreshIfDue().join();
        Assertions.assertThat(reads).hasSize(2);
    }

    @Test
    void deniedLoginKeepsNoCounts() {
        this.punish(PunishmentType.BAN, false,
                DraftPunishment.builder(PunishmentType.BAN, PlayerVictim.of(STEVE), MODERATOR));
        this.loginGate.check(new LoginAttempt(STEVE, "Steve", this.home, null)).join();
        Assertions.assertThat(this.counts.cached(STEVE)).isEmpty();
    }

    @Test
    void shadowMutesAreCountedApart() {
        this.login();
        this.punish(PunishmentType.MUTE, true, DraftPunishment.builder(PunishmentType.MUTE,
                PlayerVictim.of(STEVE), MODERATOR).shadow(true));
        Assertions.assertThat(this.steve().mutes()).isOne();
        Assertions.assertThat(this.steve().shadowMutes()).isOne();
        Assertions.assertThat(this.steve().visibleMutes()).isZero();
    }
}
