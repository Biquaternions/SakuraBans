package net.serlith.sakurabans.api.integration;

/**
 * An integration's registration. Implemented by SakuraBans; don't implement it.
 */
public interface Registration {

    /**
     * Whether SakuraBans still uses the integration.
     *
     * @return {@code false} after {@link #unregister()}
     */
    boolean active();

    /**
     * Stops using the integration. Calling it again does nothing. Unregister when your plugin
     * disables.
     */
    void unregister();
}
