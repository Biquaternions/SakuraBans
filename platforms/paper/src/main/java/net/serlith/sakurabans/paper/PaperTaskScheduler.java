package net.serlith.sakurabans.paper;

import java.util.UUID;
import net.serlith.sakurabans.core.platform.TaskScheduler;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

final class PaperTaskScheduler implements TaskScheduler {

    private final Plugin plugin;

    PaperTaskScheduler(final Plugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public void runGlobal(final Runnable task) {
        this.plugin.getServer().getGlobalRegionScheduler().execute(this.plugin, task);
    }

    @Override
    public void runForPlayer(final UUID uuid, final Runnable task) {
        final Player player = this.plugin.getServer().getPlayer(uuid);
        if (player != null) {
            this.runFor(player, task);
        }
    }

    /**
     * Runs the task on the player's thread (Folia: its region). False when it isn't scheduled:
     * the entity scheduler returns no task once the player is gone (retired).
     */
    boolean runFor(final Player player, final Runnable task) {
        return player.getScheduler().run(this.plugin, ignored -> task.run(), null) != null;
    }

}
