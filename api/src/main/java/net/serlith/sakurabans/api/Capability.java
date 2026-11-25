package net.serlith.sakurabans.api;

/**
 * Optional features, reported by {@link SakuraBansApi#capabilities()}. A capability is listed
 * only when this SakuraBans supports it; new ones are added in minor versions.
 */
public enum Capability {

    /** Punishment kinds as data, with create, update and delete. */
    KINDS,

    /** Shadow mutes: the muted player's messages are shown to themselves only. */
    SHADOW_MUTE,

    /** Evidence references on punishments. */
    EVIDENCE,

    /** Voiding punishments that were a mistake. */
    VOID,

    /** Player summaries, staff statistics and histograms. */
    STATS,

    /** Querying the audit log. */
    AUDIT,

    /** Exporting and erasing a player's data. */
    PLAYER_DATA,

    /** Bulk import of punishments through the API. */
    IMPORT,

    /** Revoking everything an operator issued since a given time. */
    ROLLBACK,

    /** Cursor-based paging of query results. */
    CURSORS
}
