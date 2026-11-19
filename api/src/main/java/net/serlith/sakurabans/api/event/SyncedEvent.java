package net.serlith.sakurabans.api.event;

/**
 * A post-event that fires on every instance of a network: on the instance that performed the
 * action, and on the others once the sync delivers it. Act on the local one only when your
 * reaction must happen exactly once (for example a webhook), by checking {@link #remote()}.
 */
public interface SyncedEvent extends SakuraEvent {

    /**
     * Whether the action happened on another instance.
     *
     * @return {@code false} on the instance that performed it, {@code true} elsewhere
     */
    boolean remote();
}
