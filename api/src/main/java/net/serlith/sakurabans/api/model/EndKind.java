package net.serlith.sakurabans.api.model;

/**
 * Why a punishment stopped being active.
 */
public enum EndKind {

    /** An operator revoked it (unban, unmute, unwarn). */
    REVOKED,

    /** Its expiry passed. */
    EXPIRED,

    /** A newer punishment replaced it (override). */
    SUPERSEDED,

    /** Undone together with everything an operator issued ({@code /staffrollback}). */
    ROLLED_BACK,

    /** Ended in bulk by {@code /sb reset --mode deactivate}. */
    PRUNED,

    /** Voided: it was a mistake (see {@link Punishment#voiding()}). */
    VOIDED
}
