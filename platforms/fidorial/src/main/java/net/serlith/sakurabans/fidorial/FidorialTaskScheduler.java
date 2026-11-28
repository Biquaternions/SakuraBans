package net.serlith.sakurabans.fidorial;

import fr.fidorial.entity.Player;
import fr.fidorial.plugin.PluginContext;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import net.serlith.sakurabans.core.platform.TaskScheduler;

import java.util.UUID;

@RequiredArgsConstructor
final class FidorialTaskScheduler implements TaskScheduler {

    @Getter
    private final PluginContext ctx;

    @Override
    public void runGlobal(Runnable task) {
        throw new IllegalStateException("No global scheduler");
    }

    @Override
    public void runForPlayer(UUID uuid, Runnable task) {
        this.ctx.server().player(uuid).ifPresent(player -> {
            player.execute(task);
        });
    }

    public void runForPlayer(Player player, Runnable task) {
        player.execute(task);
    }

}
