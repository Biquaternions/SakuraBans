package net.serlith.sakurabans.core.placeholder;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Supplier;
import net.kyori.adventure.text.logger.slf4j.ComponentLogger;

public final class PlaceholderBridges {

    public interface Registration {

        void unregister();
    }

    private final ComponentLogger logger;
    private final List<Registration> registered = new CopyOnWriteArrayList<>();

    public PlaceholderBridges(final ComponentLogger logger) {
        this.logger = logger;
    }

    public boolean start(final String plugin, final boolean enabled,
            final Supplier<Registration> factory) {
        if (!enabled) {
            return false;
        }
        try {
            this.registered.add(factory.get());
            this.logger.info("Placeholders registered with {}", plugin);
            return true;
        } catch (final LinkageError | RuntimeException e) {
            this.logger.warn("{} placeholders are off: this version of {} doesn't fit ({}: {})",
                    plugin, plugin, e.getClass().getName(), e.getMessage());
            return false;
        }
    }

    public void stop() {
        for (final Registration registration : this.registered) {
            try {
                registration.unregister();
            } catch (final LinkageError | RuntimeException e) {
                this.logger.warn("Couldn't unregister placeholders ({}: {})",
                        e.getClass().getName(), e.getMessage());
            }
        }
        this.registered.clear();
    }

    public int count() {
        return this.registered.size();
    }
}
