package net.serlith.sakurabans.api.stats;

/**
 * The width of a histogram bucket. Buckets start at the beginning of the unit in the requested
 * time zone (midnight for days, Monday for weeks, the 1st for months).
 */
public enum Bucket {

    /** One hour. */
    HOUR,

    /** One day. */
    DAY,

    /** One week, starting on Monday. */
    WEEK,

    /** One calendar month. */
    MONTH
}
