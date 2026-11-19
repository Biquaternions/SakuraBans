package net.serlith.sakurabans.core.fake;

import java.util.UUID;
import java.util.function.Predicate;
import net.serlith.sakurabans.core.platform.TaskScheduler;

/** Runs every hop on the calling thread; player hops only while the player is online. */
public final class ImmediateTaskScheduler implements TaskScheduler {

    private final Predicate<UUID> online;

    public ImmediateTaskScheduler(final Predicate<UUID> online) {
        this.online = online;
    }

    @Override
    public void runGlobal(final Runnable task) {
        task.run();
    }

    @Override
    public void runForPlayer(final UUID player, final Runnable task) {
        if (this.online.test(player)) {
            task.run();
        }
    }
}
