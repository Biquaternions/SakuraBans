package net.serlith.sakurabans.core.admin;

import java.net.InetAddress;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Set;
import net.serlith.sakurabans.api.model.Actor;
import net.serlith.sakurabans.api.model.CompositeVictim;
import net.serlith.sakurabans.api.model.PlayerOperator;
import net.serlith.sakurabans.api.model.PlayerVictim;
import net.serlith.sakurabans.api.model.Punishment;
import net.serlith.sakurabans.api.model.PunishmentType;
import net.serlith.sakurabans.api.model.Victim;
import net.serlith.sakurabans.api.punish.DraftPunishment;
import net.serlith.sakurabans.api.punish.PunishOptions;
import net.serlith.sakurabans.api.punish.PunishResult;
import net.serlith.sakurabans.api.punish.PunishmentQuery;
import net.serlith.sakurabans.api.punish.RevocationRequest;
import net.serlith.sakurabans.api.punish.RevocationTarget;
import net.serlith.sakurabans.api.stats.Bucket;
import net.serlith.sakurabans.api.stats.CountBucket;
import net.serlith.sakurabans.api.stats.PlayerSummary;
import net.serlith.sakurabans.api.stats.StaffStats;
import net.serlith.sakurabans.core.ServiceTest;
import net.serlith.sakurabans.core.storage.repository.StatsRepository;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Player summaries, staff stats and histograms (plan S37b). */
class StatsTest extends ServiceTest {

    private static final Actor OTHER = Actor.of(PlayerOperator.of(ALEX, "Alex"), "in-game");

    private SakuraStatsService stats;
    private Instant start;

    @BeforeEach
    void stats() {
        this.stats = new SakuraStatsService(new StatsRepository(this.storage, this.allowList),
                this.punishments, this.clock);
        this.start = this.now.get();
    }

    private Punishment punish(final PunishmentType type, final Victim victim, final Actor by) {
        return ((PunishResult.Success) this.service.punish(DraftPunishment.builder(type, victim,
                by).duration(type == PunishmentType.KICK ? null : Duration.ofDays(30)).build(),
                PunishOptions.defaults()).join()).punishment();
    }

    @Test
    void summariesCountAnAccountsPunishmentsButNotVoidedOnes() throws Exception {
        final Punishment ban = this.punish(PunishmentType.BAN, PlayerVictim.of(STEVE), MODERATOR);
        this.now.set(this.now.get().plusSeconds(60));
        final Punishment mute = this.punish(PunishmentType.MUTE, CompositeVictim.of(STEVE,
                InetAddress.getByName("203.0.113.9")), MODERATOR);
        this.now.set(this.now.get().plusSeconds(60));
        final Punishment warn = this.punish(PunishmentType.WARN, PlayerVictim.of(STEVE), OTHER);
        this.service.voidPunishment(warn.id(), MODERATOR, null).join();
        this.punish(PunishmentType.BAN, PlayerVictim.of(ALEX), MODERATOR);

        final PlayerSummary summary = this.stats.playerSummary(STEVE).join();
        Assertions.assertThat(summary.active()).extracting(Punishment::id)
                .containsExactly(mute.id(), ban.id());
        Assertions.assertThat(summary.counts()).containsOnly(
                Assertions.entry("ban", 1L), Assertions.entry("mute", 1L));
        Assertions.assertThat(summary.voided()).isEqualTo(1);
        Assertions.assertThat(summary.last()).hasValueSatisfying(last ->
                Assertions.assertThat(last.id()).isEqualTo(mute.id()));
        Assertions.assertThat(this.stats.playerSummary(MOD).join().last()).isEmpty();
    }

    @Test
    void staffStatsCountIssuedRevokedAndVoided() {
        final Punishment ban = this.punish(PunishmentType.BAN, PlayerVictim.of(STEVE), OTHER);
        this.punish(PunishmentType.KICK, PlayerVictim.of(STEVE), OTHER);
        final Punishment warn = this.punish(PunishmentType.WARN, PlayerVictim.of(STEVE), OTHER);
        this.service.revoke(RevocationRequest.builder(RevocationTarget.ById.of(ban.id()),
                MODERATOR).build()).join();
        this.service.voidPunishment(warn.id(), MODERATOR, null).join();
        final Instant end = this.now.get().plusSeconds(1);

        final StaffStats alex = this.stats.staffStats(OTHER.operator(), this.start, end).join();
        Assertions.assertThat(alex.issued()).containsOnly(Assertions.entry("ban", 1L),
                Assertions.entry("kick", 1L));
        Assertions.assertThat(alex.revoked()).isEmpty();
        final StaffStats mod = this.stats.staffStats(MODERATOR.operator(), this.start, end)
                .join();
        Assertions.assertThat(mod.revoked()).containsOnly(Assertions.entry("ban", 1L));
        Assertions.assertThat(mod.voided()).containsOnly(Assertions.entry("warn", 1L));
        Assertions.assertThat(this.stats.staffStats(OTHER.operator(), end,
                end.plusSeconds(60)).join().issued()).isEmpty();
    }

    @Test
    void histogramsBucketByTimeAndKind() {
        this.now.set(Instant.parse("2026-11-16T10:30:00Z"));
        this.punish(PunishmentType.BAN, PlayerVictim.of(STEVE), MODERATOR);
        this.now.set(Instant.parse("2026-11-16T10:50:00Z"));
        this.punish(PunishmentType.KICK, PlayerVictim.of(STEVE), MODERATOR);
        this.now.set(Instant.parse("2026-11-22T23:59:00Z"));
        this.punish(PunishmentType.KICK, PlayerVictim.of(ALEX), MODERATOR);
        this.now.set(Instant.parse("2026-12-01T00:00:00Z"));
        this.punish(PunishmentType.KICK, PlayerVictim.of(ALEX), MODERATOR);

        final List<CountBucket> hours = this.stats.histogram(PunishmentQuery.builder().build(),
                Bucket.HOUR, ZoneOffset.UTC).join();
        Assertions.assertThat(hours).extracting(CountBucket::start, CountBucket::kind,
                CountBucket::count).containsExactly(
                Assertions.tuple(Instant.parse("2026-11-16T10:00:00Z"), "ban", 1L),
                Assertions.tuple(Instant.parse("2026-11-16T10:00:00Z"), "kick", 1L),
                Assertions.tuple(Instant.parse("2026-11-22T23:00:00Z"), "kick", 1L),
                Assertions.tuple(Instant.parse("2026-12-01T00:00:00Z"), "kick", 1L));
        final List<CountBucket> weeks = this.stats.histogram(PunishmentQuery.builder()
                .types(Set.of(PunishmentType.KICK)).build(), Bucket.WEEK,
                ZoneOffset.UTC).join();
        Assertions.assertThat(weeks).extracting(CountBucket::start, CountBucket::count)
                .containsExactly(
                        Assertions.tuple(Instant.parse("2026-11-16T00:00:00Z"), 2L),
                        Assertions.tuple(Instant.parse("2026-11-30T00:00:00Z"), 1L));
        // Buckets start at local midnight: in UTC+2 a month starts at 22:00 UTC.
        Assertions.assertThat(this.stats.histogram(PunishmentQuery.builder().build(),
                Bucket.MONTH, ZoneOffset.ofHours(2)).join()).extracting(CountBucket::start)
                .contains(Instant.parse("2026-10-31T22:00:00Z"),
                        Instant.parse("2026-11-30T22:00:00Z"));
    }
}
