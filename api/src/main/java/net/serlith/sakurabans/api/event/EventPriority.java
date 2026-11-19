package net.serlith.sakurabans.api.event;

/**
 * The order handlers run in, from {@link #LOWEST} to {@link #MONITOR}. Handlers with the same
 * priority run in subscription order.
 */
public enum EventPriority {

    /** Runs first; later handlers can override its changes. */
    LOWEST,

    /** Runs after {@link #LOWEST}. */
    LOW,

    /** The default. */
    NORMAL,

    /** Runs after {@link #NORMAL}. */
    HIGH,

    /** Runs last among the handlers that may change the event. */
    HIGHEST,

    /** Runs after everything else, to observe the final state. Must not change the event. */
    MONITOR
}
