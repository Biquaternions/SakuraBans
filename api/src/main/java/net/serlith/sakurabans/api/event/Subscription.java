package net.serlith.sakurabans.api.event;

/**
 * A handler's registration with the {@link EventBus}. Implemented by SakuraBans; don't implement
 * it.
 */
public interface Subscription {

    /**
     * Whether the handler still receives events.
     *
     * @return {@code false} after {@link #unsubscribe()}
     */
    boolean active();

    /**
     * Stops delivering events to the handler. Calling it again does nothing. Unsubscribe when
     * your plugin disables; SakuraBans also drops every subscription when it stops.
     */
    void unsubscribe();
}
