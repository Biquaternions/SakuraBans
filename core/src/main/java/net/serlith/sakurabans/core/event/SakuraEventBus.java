package net.serlith.sakurabans.core.event;

import com.google.common.base.Preconditions;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import net.kyori.adventure.text.logger.slf4j.ComponentLogger;
import net.serlith.sakurabans.api.event.Cancellable;
import net.serlith.sakurabans.api.event.EventBus;
import net.serlith.sakurabans.api.event.EventPriority;
import net.serlith.sakurabans.api.event.SakuraEvent;
import net.serlith.sakurabans.api.event.Subscription;

public final class SakuraEventBus implements EventBus {

    private static final Comparator<Handler> ORDER =
            Comparator.comparing(Handler::priority).thenComparingLong(Handler::sequence);

    private final ComponentLogger logger;
    private final AtomicLong sequence = new AtomicLong();
    private final AtomicReference<List<Handler>> handlers = new AtomicReference<>(List.of());

    public SakuraEventBus(final ComponentLogger logger) {
        this.logger = logger;
    }

    private record Handler(Class<? extends SakuraEvent> type, EventPriority priority,
            long sequence, Consumer<Object> consumer) {
    }

    @Override
    public <E extends SakuraEvent> Subscription subscribe(final Class<E> type,
            final EventPriority priority, final Consumer<? super E> handler) {
        Preconditions.checkNotNull(type, "type");
        Preconditions.checkNotNull(priority, "priority");
        Preconditions.checkNotNull(handler, "handler");
        final Handler entry = new Handler(type, priority, this.sequence.incrementAndGet(),
                event -> handler.accept(type.cast(event)));
        this.handlers.updateAndGet(current -> {
            final List<Handler> next = new ArrayList<>(current);
            next.add(entry);
            next.sort(ORDER);
            return List.copyOf(next);
        });
        return new Subscription() {
            @Override
            public boolean active() {
                return SakuraEventBus.this.handlers.get().contains(entry);
            }

            @Override
            public void unsubscribe() {
                SakuraEventBus.this.handlers.updateAndGet(current -> current.stream()
                        .filter(existing -> existing != entry)
                        .toList());
            }
        };
    }

    public <E extends SakuraEvent> E post(final E event) {
        Preconditions.checkNotNull(event, "event");
        Boolean cancelledBeforeMonitor = null;
        for (final Handler handler : this.handlers.get()) {
            if (!handler.type().isInstance(event)) {
                continue;
            }
            final boolean monitor = handler.priority() == EventPriority.MONITOR;
            if (monitor && cancelledBeforeMonitor == null
                    && event instanceof Cancellable cancellable) {
                cancelledBeforeMonitor = cancellable.cancelled();
            }
            try {
                handler.consumer().accept(event);
            } catch (final RuntimeException | LinkageError e) {
                this.logger.error("An event handler for {} failed ({})",
                        event.getClass().getSimpleName(), handler.type().getSimpleName(), e);
            }
            if (monitor && event instanceof Cancellable cancellable
                    && cancellable.cancelled() != cancelledBeforeMonitor) {
                cancellable.cancelled(cancelledBeforeMonitor);
                this.logger.warn("A MONITOR handler for {} changed whether it is cancelled;"
                        + " MONITOR handlers may only observe", handler.type().getSimpleName());
            }
        }
        return event;
    }

}
