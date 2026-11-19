package net.serlith.sakurabans.api.event;

/**
 * An event whose action can be stopped. What cancelling means is documented on each event.
 */
public interface Cancellable extends SakuraEvent {

    /**
     * Whether the action is cancelled so far. Later handlers may change it again.
     *
     * @return {@code true} if cancelled
     */
    boolean cancelled();

    /**
     * Cancels the action, or un-cancels it.
     *
     * @param cancelled {@code true} to cancel
     */
    void cancelled(boolean cancelled);
}
