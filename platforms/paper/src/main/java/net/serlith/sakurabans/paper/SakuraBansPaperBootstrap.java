package net.serlith.sakurabans.paper;

import io.papermc.paper.plugin.bootstrap.BootstrapContext;
import io.papermc.paper.plugin.bootstrap.PluginBootstrap;
import io.papermc.paper.plugin.bootstrap.PluginProviderContext;
import net.serlith.sakurabans.core.SakuraBansCore;
import org.bukkit.plugin.java.JavaPlugin;
import org.jspecify.annotations.Nullable;

public final class SakuraBansPaperBootstrap implements PluginBootstrap {

    private @Nullable SakuraBansCore core;

    @Override
    public void bootstrap(final BootstrapContext context) {
        this.core = new SakuraBansCore(context.getLogger());
    }

    @Override
    public JavaPlugin createPlugin(final PluginProviderContext context) {
        final SakuraBansCore core = this.core;
        if (core == null) {
            throw new IllegalStateException("createPlugin was called before bootstrap");
        }
        return new SakuraBansPaper(core);
    }
}
