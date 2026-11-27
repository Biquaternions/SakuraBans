package net.serlith.sakurabans.velocity;

import com.velocitypowered.api.proxy.Player;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.function.Supplier;
import net.kyori.adventure.text.Component;
import org.jspecify.annotations.Nullable;

/**
 * The packetevents bridge behind a guard: its code never ran against a real proxy, so
 * whatever it throws, including a {@link LinkageError} when the bridge class itself can't be
 * loaded against the installed packetevents, makes {@link #show} return {@code false} and the
 * core send the message as chat. The first failure is reported once, not per player. A
 * {@link LinkageError} turns dialogs off until a restart; another failure only skips that one.
 */
final class GuardedNotices implements VelocityNotices {

    private final Supplier<VelocityNotices> factory;
    private final Consumer<Throwable> report;
    private final AtomicBoolean reported = new AtomicBoolean();
    private volatile @Nullable VelocityNotices bridge;
    private volatile boolean broken;

    GuardedNotices(final Supplier<VelocityNotices> factory, final Consumer<Throwable> report) {
        this.factory = factory;
        this.report = report;
    }

    @Override
    public boolean show(final Player player, final Component title, final Component body,
            final Component button) {
        if (this.broken) {
            return false;
        }
        try {
            VelocityNotices current = this.bridge;
            if (current == null) {
                current = this.factory.get();
                this.bridge = current;
            }
            return current.show(player, title, body, button);
        } catch (final LinkageError e) {
            this.broken = true;
            this.failed(e);
            return false;
        } catch (final RuntimeException e) {
            this.failed(e);
            return false;
        }
    }

    private void failed(final Throwable failure) {
        if (this.reported.compareAndSet(false, true)) {
            this.report.accept(failure);
        }
    }
}
