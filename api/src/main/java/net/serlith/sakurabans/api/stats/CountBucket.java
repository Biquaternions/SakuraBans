package net.serlith.sakurabans.api.stats;

import java.time.Instant;

/**
 * The number of punishments of one kind created in one histogram bucket. Implemented by
 * SakuraBans; don't implement it.
 */
public interface CountBucket {

    /**
     * When the bucket starts.
     *
     * @return the start
     */
    Instant start();

    /**
     * The kind counted.
     *
     * @return the kind id
     */
    String kind();

    /**
     * How many were created.
     *
     * @return the count, at least 1 (empty buckets are left out)
     */
    long count();
}
