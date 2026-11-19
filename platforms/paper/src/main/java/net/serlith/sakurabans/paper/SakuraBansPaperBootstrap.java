package net.serlith.sakurabans.paper;

import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.plugin.bootstrap.BootstrapContext;
import io.papermc.paper.plugin.bootstrap.PluginBootstrap;
import io.papermc.paper.plugin.bootstrap.PluginProviderContext;
import net.serlith.sakurabans.core.SakuraBansCore;
import net.serlith.sakurabans.core.bootstrap.PackagingChecks;
import org.bukkit.plugin.java.JavaPlugin;
import org.incendo.cloud.execution.ExecutionCoordinator;
import org.incendo.cloud.paper.PaperCommandManager;
import org.jspecify.annotations.Nullable;

@SuppressWarnings("UnstableApiUsage")
public final class SakuraBansPaperBootstrap implements PluginBootstrap {

    private @Nullable SakuraBansCore core;
    private PaperCommandManager.@Nullable Bootstrapped<CommandSourceStack> commands;

    public SakuraBansPaperBootstrap() {
    }

    @Override
    public void bootstrap(final BootstrapContext context) {
        this.core = new SakuraBansCore(context.getLogger());
        if (PackagingChecks.enabled()) {
            PackagingChecks.checkLibraries(context.getLogger(), context.getDataDirectory());
            final PaperCommandManager.Bootstrapped<CommandSourceStack> manager =
                    PaperCommandManager.builder()
                            .executionCoordinator(ExecutionCoordinator.simpleCoordinator())
                            .buildBootstrapped(context);
            PackagingChecks.registerCloudCheck(context.getLogger(), manager);
            this.commands = manager;
        }
    }

    @Override
    public JavaPlugin createPlugin(final PluginProviderContext context) {
        final SakuraBansCore core = this.core;
        if (core == null) {
            throw new IllegalStateException("createPlugin was called before bootstrap");
        }
        return new SakuraBansPaper(core, this.commands);
    }
}
