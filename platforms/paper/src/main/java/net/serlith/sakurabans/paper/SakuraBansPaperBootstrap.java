package net.serlith.sakurabans.paper;

import io.papermc.paper.plugin.bootstrap.BootstrapContext;
import io.papermc.paper.plugin.bootstrap.PluginBootstrap;
import io.papermc.paper.plugin.bootstrap.PluginProviderContext;
import net.serlith.sakurabans.core.SakuraBansCore;
import net.serlith.sakurabans.core.bootstrap.CoreBootstrap;
import net.serlith.sakurabans.core.bootstrap.CoreEnvironment;
import net.serlith.sakurabans.core.platform.PlatformType;
import org.bukkit.plugin.java.JavaPlugin;
import org.jspecify.annotations.Nullable;

@SuppressWarnings("UnstableApiUsage")
public final class SakuraBansPaperBootstrap implements PluginBootstrap {

    private @Nullable SakuraBansCore core;

    public SakuraBansPaperBootstrap() {
    }

    @Override
    public void bootstrap(final BootstrapContext context) {
        this.core = CoreBootstrap.create(new CoreEnvironment(
                context.getDataDirectory(), context.getLogger(), PlatformType.PAPER));
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
