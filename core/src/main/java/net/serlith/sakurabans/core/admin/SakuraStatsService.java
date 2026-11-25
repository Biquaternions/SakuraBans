package net.serlith.sakurabans.core.admin;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.InstantSource;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import net.serlith.sakurabans.api.model.Operator;
import net.serlith.sakurabans.api.punish.PunishmentQuery;
import net.serlith.sakurabans.api.stats.Bucket;
import net.serlith.sakurabans.api.stats.CountBucket;
import net.serlith.sakurabans.api.stats.PlayerSummary;
import net.serlith.sakurabans.api.stats.StaffStats;
import net.serlith.sakurabans.api.stats.StatsService;
import net.serlith.sakurabans.core.model.ApiValues;
import net.serlith.sakurabans.core.model.SakuraStats;
import net.serlith.sakurabans.core.storage.repository.PunishmentRepository;
import net.serlith.sakurabans.core.storage.repository.StatsRepository;

public final class SakuraStatsService implements StatsService {

    private static final int ACTIVE_LIMIT = 500;

    private final StatsRepository stats;
    private final PunishmentRepository punishments;
    private final InstantSource clock;

    public SakuraStatsService(final StatsRepository stats, final PunishmentRepository punishments,
            final InstantSource clock) {
        this.stats = stats;
        this.punishments = punishments;
        this.clock = clock;
    }

    @Override
    public CompletableFuture<PlayerSummary> playerSummary(final UUID uuid) {
        final Instant now = this.clock.instant();
        return this.punishments.namingAccount(uuid, true, ACTIVE_LIMIT, now)
                .thenCompose(active -> this.punishments.namingAccount(uuid, false, 1, now)
                        .thenCompose(last -> this.stats.account(uuid).thenApply(counts ->
                                new SakuraStats.Summary(uuid, active, counts.perKind(),
                                        counts.voided(),
                                        last.isEmpty() ? null : last.getFirst()))));
    }

    @Override
    public CompletableFuture<StaffStats> staffStats(final Operator operator, final Instant from,
            final Instant to) {
        final Operator checked;
        try {
            checked = ApiValues.operator(operator);
            if (!from.isBefore(to)) {
                throw new IllegalArgumentException("from must be before to");
            }
        } catch (final RuntimeException e) {
            return CompletableFuture.failedFuture(e);
        }
        return this.stats.staff(checked, from, to).thenApply(counts -> new SakuraStats.Staff(
                checked, from, to, counts.issued(), counts.revoked(), counts.voided()));
    }

    @Override
    public CompletableFuture<List<CountBucket>> histogram(final PunishmentQuery query,
            final Bucket bucket, final ZoneId zone) {
        final PunishmentQuery checked;
        try {
            checked = query.toBuilder().build();
        } catch (final RuntimeException e) {
            return CompletableFuture.failedFuture(e);
        }
        final Map<Instant, Map<String, Long>> counts = new HashMap<>();
        return this.stats.created(checked, this.clock.instant(), (created, kind) ->
                        counts.computeIfAbsent(start(created, bucket, zone),
                                ignored -> new HashMap<>()).merge(kind, 1L, Long::sum))
                .thenApply(ignored -> {
                    final List<CountBucket> buckets = new ArrayList<>();
                    counts.forEach((start, kinds) -> kinds.forEach((kind, count) ->
                            buckets.add(new SakuraStats.Count(start, kind, count))));
                    buckets.sort(Comparator.comparing(CountBucket::start)
                            .thenComparing(CountBucket::kind));
                    return List.copyOf(buckets);
                });
    }

    static Instant start(final Instant at, final Bucket bucket, final ZoneId zone) {
        final ZonedDateTime time = at.atZone(zone);
        return switch (bucket) {
            case HOUR -> time.truncatedTo(ChronoUnit.HOURS).toInstant();
            case DAY -> time.truncatedTo(ChronoUnit.DAYS).toInstant();
            case WEEK -> time.truncatedTo(ChronoUnit.DAYS)
                    .with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).toInstant();
            case MONTH -> time.truncatedTo(ChronoUnit.DAYS)
                    .with(TemporalAdjusters.firstDayOfMonth()).toInstant();
        };
    }

}
