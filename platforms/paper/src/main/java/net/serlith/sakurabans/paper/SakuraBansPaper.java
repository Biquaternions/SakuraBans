package net.serlith.sakurabans.paper;

import net.serlith.sakurabans.core.SakuraBansCore;
import org.bukkit.plugin.java.JavaPlugin;

public final class SakuraBansPaper extends JavaPlugin {

    private final SakuraBansCore core;

    SakuraBansPaper(final SakuraBansCore core) {
        this.core = core;
    }

    @Override
    public void onEnable() {
        this.core.start(new PaperPlatform(this));
    }

    @Override
    public void onDisable() {
        this.core.stop();
    }
}
