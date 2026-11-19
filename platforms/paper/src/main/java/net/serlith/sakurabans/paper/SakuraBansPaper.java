package net.serlith.sakurabans.paper;

import net.serlith.sakurabans.core.SakuraBansCore;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Paper entry point. Receives the core from {@link SakuraBansPaperBootstrap} and starts it on
 * enable (phase 2, plan 6.3).
 */
public final class SakuraBansPaper extends JavaPlugin {

    private final SakuraBansCore core;

    SakuraBansPaper(final SakuraBansCore core) {
        this.core = core;
    }

    @Override
    public void onEnable() {
        this.core.start();
    }

    @Override
    public void onDisable() {
        this.core.stop();
    }
}
