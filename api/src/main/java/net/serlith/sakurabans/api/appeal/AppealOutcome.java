package net.serlith.sakurabans.api.appeal;

/**
 * How staff decide an appeal.
 */
public enum AppealOutcome {

    /** The punishment stands. */
    REJECT,

    /** The punishment is revoked (it ends now, and still counts in the history). */
    REVOKE,

    /** The punishment is voided (it was a mistake and no longer counts). */
    VOID
}
