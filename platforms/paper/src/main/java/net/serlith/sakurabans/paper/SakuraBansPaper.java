package net.serlith.sakurabans.paper;

import io.papermc.paper.command.brigadier.CommandSourceStack;
import net.serlith.sakurabans.core.SakuraBansCore;
import org.bukkit.plugin.java.JavaPlugin;
import org.incendo.cloud.paper.PaperCommandManager;
import org.jspecify.annotations.Nullable;

public final class SakuraBansPaper extends JavaPlugin {

    private final SakuraBansCore core;
    private final PaperCommandManager.@Nullable Bootstrapped<CommandSourceStack> commands;

    SakuraBansPaper(final SakuraBansCore core,
            final PaperCommandManager.@Nullable Bootstrapped<CommandSourceStack> commands) {
        this.core = core;
        this.commands = commands;
    }

    @Override
    public void onEnable() {
        if (this.commands != null) {
            this.commands.onEnable();
        }
        this.core.start();
    }

    @Override
    public void onDisable() {
        this.core.stop();
    }
}
