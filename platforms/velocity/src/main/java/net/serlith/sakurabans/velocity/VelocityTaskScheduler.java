package net.serlith.sakurabans.velocity;

import com.velocitypowered.api.proxy.ProxyServer;
import java.util.UUID;
import net.serlith.sakurabans.core.platform.TaskScheduler;

final class VelocityTaskScheduler implements TaskScheduler {

    private final ProxyServer proxy;

    VelocityTaskScheduler(final ProxyServer proxy) {
        this.proxy = proxy;
    }

    @Override
    public void runGlobal(final Runnable task) {
        task.run();
    }

    @Override
    public void runForPlayer(final UUID uuid, final Runnable task) {
        if (this.proxy.getPlayer(uuid).isPresent()) {
            task.run();
        }
    }

}
