package net.serlith.sakurabans.core.model;

public enum AuditAction {
    CREATE,
    REVOKE,
    EDIT_REASON,
    EDIT_EXPIRY,
    EDIT_SCOPE,
    VOID,
    EXPUNGE,
    ROLLBACK,
    EVIDENCE_ATTACH,
    EVIDENCE_DETACH,
    UPDATE,
    DELETE,
    FORGET_ADDRESS,
    EXPORT,
    ERASE,
    ALLOW,
    DISALLOW,
    IMPORT,
    RESET,
    /** A ladder step without a punishment: only its use and actions. */
    APPLY
}
