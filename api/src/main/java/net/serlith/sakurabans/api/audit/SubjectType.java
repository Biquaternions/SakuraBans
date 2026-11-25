package net.serlith.sakurabans.api.audit;

/**
 * What an audit entry is about. Its {@link AuditEntry#subjectId()} identifies the entity.
 */
public enum SubjectType {

    /** A punishment; the id is its database id. */
    PUNISHMENT,

    /** A punishment kind; the id is the kind id. */
    KIND,

    /** A template; the id is its name. */
    TEMPLATE,

    /** The network-wide settings; the id is the changed key. */
    SETTINGS,

    /** A message template; the id is {@code <locale>/<key>}. */
    MESSAGE,

    /** A player's data; the id is the account's UUID. */
    PLAYER,

    /** The allow list; the id is the UUID or address. */
    ALLOW_LIST,

    /** A bulk import; the id is the import's id. */
    IMPORT
}
