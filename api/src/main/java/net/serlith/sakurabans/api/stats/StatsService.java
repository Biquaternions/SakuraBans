package net.serlith.sakurabans.api.stats;

import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import net.serlith.sakurabans.api.model.Operator;
import net.serlith.sakurabans.api.punish.PunishmentQuery;

/**
 * Summaries and statistics. Every method returns a future completed on a worker thread.
 * Implemented by SakuraBans; don't implement it.
 */
public interface StatsService {

    /**
     * An account's punishment record at a glance.
     *
     * @param uuid the account
     * @return the summary
     */
    CompletableFuture<PlayerSummary> playerSummary(UUID uuid);

    /**
     * A staff member's activity in a time range.
     *
     * @param operator the staff member, matched by identity
     * @param from the start (inclusive)
     * @param to the end (exclusive), after {@code from}
     * @return the statistics
     */
    CompletableFuture<StaffStats> staffStats(Operator operator, Instant from, Instant to);

    /**
     * Counts the punishments a query selects per bucket of creation time and per kind, for
     * dashboard charts. The query's paging is ignored.
     *
     * @param query the filters; use its creation bounds to limit the range
     * @param bucket the bucket width
     * @param zone the time zone buckets start in
     * @return the non-empty buckets, oldest first
     */
    CompletableFuture<List<CountBucket>> histogram(
            PunishmentQuery query, Bucket bucket, ZoneId zone);
}
