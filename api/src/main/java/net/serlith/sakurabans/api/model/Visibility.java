package net.serlith.sakurabans.api.model;

/**
 * Who is told about a punishment.
 */
public enum Visibility {

    /** Broadcast to staff with the notify permission, and to players if configured. */
    NORMAL,

    /** Only staff with the silent notify permission are told. */
    SILENT,

    /** Nobody is told, and it is left out of public lists. */
    HIDDEN
}
