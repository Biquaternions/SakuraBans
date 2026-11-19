package net.serlith.sakurabans.api.punish;

/**
 * Where a new or revoked punishment takes effect right away. It is always stored; this only
 * decides who acts on it immediately (kicking online victims, updating mute caches).
 */
public enum Enforcement {

    /** On this instance and, through sync, on every other instance. */
    GLOBAL,

    /** On this instance only. Other instances see it on their next check. */
    LOCAL_ONLY,

    /** Nowhere: the punishment is only recorded (imports, history entries). */
    NONE
}
