package net.serlith.sakurabans.api.event;

import java.util.function.Consumer;

/**
 * Delivers SakuraBans events to handlers. Implemented by SakuraBans; don't implement it.
 */
public interface EventBus {

    /**
     * Subscribes a handler. It also receives subtypes of {@code type}, so subscribing to
     * {@link SakuraEvent} receives every event. Events are local to the server where the action
     * happens.
     *
     * @param type the event type
     * @param priority when the handler runs relative to others
     * @param handler the handler; it runs on the thread the event documents and must not block
     * @param <E> the event type
     * @return the subscription
     */
    <E extends SakuraEvent> Subscription subscribe(
            Class<E> type, EventPriority priority, Consumer<? super E> handler);

    /**
     * Subscribes a handler with {@link EventPriority#NORMAL}.
     *
     * @param type the event type
     * @param handler the handler
     * @param <E> the event type
     * @return the subscription
     */
    default <E extends SakuraEvent> Subscription subscribe(
            final Class<E> type, final Consumer<? super E> handler) {
        return this.subscribe(type, EventPriority.NORMAL, handler);
    }
}
