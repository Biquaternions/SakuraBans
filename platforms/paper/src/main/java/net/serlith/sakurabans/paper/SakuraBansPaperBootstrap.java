package net.serlith.sakurabans.paper;

import io.papermc.paper.plugin.bootstrap.BootstrapContext;
import io.papermc.paper.plugin.bootstrap.PluginBootstrap;
import io.papermc.paper.plugin.bootstrap.PluginProviderContext;
import net.serlith.sakurabans.core.SakuraBansCore;
import net.serlith.sakurabans.core.bootstrap.CoreBootstrap;
import net.serlith.sakurabans.core.bootstrap.CoreEnvironment;
import net.serlith.sakurabans.core.command.CommandActor;
import org.bukkit.plugin.java.JavaPlugin;
import org.incendo.cloud.execution.ExecutionCoordinator;
import org.incendo.cloud.paper.PaperCommandManager;
import org.jspecify.annotations.Nullable;

@SuppressWarnings("UnstableApiUsage")
public final class SakuraBansPaperBootstrap implements PluginBootstrap {

    private @Nullable SakuraBansCore core;
    private PaperCommandManager.@Nullable Bootstrapped<CommandActor> commands;

    public SakuraBansPaperBootstrap() {
    }

    @Override
    public void bootstrap(final BootstrapContext context) {
        final SakuraBansCore created = CoreBootstrap.create(new CoreEnvironment(
                context.getDataDirectory(), context.getLogger(), false));
        final PaperCommandManager.Bootstrapped<CommandActor> manager =
                PaperCommandManager.builder(new PaperSenderMapper())
                        .executionCoordinator(ExecutionCoordinator.simpleCoordinator())
                        .buildBootstrapped(context);
        created.commands().install(manager);
        this.core = created;
        this.commands = manager;
    }

    @Override
    public JavaPlugin createPlugin(final PluginProviderContext context) {
        final SakuraBansCore created = this.core;
        final PaperCommandManager.Bootstrapped<CommandActor> manager = this.commands;
        if (created == null || manager == null) {
            throw new IllegalStateException("createPlugin was called before bootstrap");
        }
        return new SakuraBansPaper(created, manager);
    }
}
