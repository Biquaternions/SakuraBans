package net.serlith.sakurabans.api.event;

/**
 * How an entity changed.
 */
public enum ChangeType {

    /** It was created. */
    CREATED,

    /** It was changed. */
    UPDATED,

    /** It was deleted. */
    DELETED
}
