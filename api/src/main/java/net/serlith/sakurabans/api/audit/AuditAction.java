package net.serlith.sakurabans.api.audit;

/**
 * What was done. New actions are added in minor versions.
 */
public enum AuditAction {

    /** A punishment or entity was created. */
    CREATE,

    /** A punishment was revoked. */
    REVOKE,

    /** A punishment's reason changed. */
    EDIT_REASON,

    /** A punishment's expiry changed. */
    EDIT_EXPIRY,

    /** A punishment's scope changed. */
    EDIT_SCOPE,

    /** A punishment was voided. */
    VOID,

    /** A punishment was deleted. */
    EXPUNGE,

    /** A punishment ended through a staff rollback. */
    ROLLBACK,

    /** Evidence was attached. */
    EVIDENCE_ATTACH,

    /** Evidence was detached. */
    EVIDENCE_DETACH,

    /** An entity (kind, template, settings, message) was changed. */
    UPDATE,

    /** An entity was deleted. */
    DELETE,

    /** An address was removed from a player's history. */
    FORGET_ADDRESS,

    /** A player's data was exported. */
    EXPORT,

    /** A player's data was erased. */
    ERASE,

    /** An account or address was allow-listed. */
    ALLOW,

    /** An account or address was removed from the allow list. */
    DISALLOW,

    /** Punishments were imported. */
    IMPORT
}
