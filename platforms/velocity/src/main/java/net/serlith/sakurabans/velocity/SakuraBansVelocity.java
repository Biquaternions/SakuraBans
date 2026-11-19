package net.serlith.sakurabans.velocity;

import com.google.inject.Inject;
import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent;
import com.velocitypowered.api.event.proxy.ProxyShutdownEvent;
import com.velocitypowered.api.plugin.Dependency;
import com.velocitypowered.api.plugin.Plugin;
import com.velocitypowered.api.plugin.PluginContainer;
import com.velocitypowered.api.plugin.annotation.DataDirectory;
import com.velocitypowered.api.proxy.ProxyServer;
import java.nio.file.Path;
import net.kyori.adventure.text.logger.slf4j.ComponentLogger;
import net.serlith.sakurabans.core.SakuraBansCore;
import net.serlith.sakurabans.core.bootstrap.PackagingChecks;
import net.serlith.sakurabans.core.util.BuildConstants;
import org.incendo.cloud.SenderMapper;
import org.incendo.cloud.execution.ExecutionCoordinator;
import org.incendo.cloud.velocity.VelocityCommandManager;

@Plugin(
        id = "sakurabans",
        name = "SakuraBans",
        version = BuildConstants.VERSION,
        authors = {"Biquaternions"},
        dependencies = {
            @Dependency(id = "luckperms", optional = true),
            @Dependency(id = "floodgate", optional = true)
        }
)
public final class SakuraBansVelocity {

    private final ProxyServer proxy;
    private final PluginContainer container;
    private final ComponentLogger logger;
    private final Path dataDirectory;
    private final SakuraBansCore core;

    @Inject
    public SakuraBansVelocity(final ProxyServer proxy, final PluginContainer container,
            final ComponentLogger logger, @DataDirectory final Path dataDirectory) {
        this.proxy = proxy;
        this.container = container;
        this.logger = logger;
        this.dataDirectory = dataDirectory;
        this.core = new SakuraBansCore(logger);
    }

    @Subscribe
    public void onProxyInitialization(final ProxyInitializeEvent event) {
        if (PackagingChecks.enabled()) {
            PackagingChecks.checkLibraries(this.logger, this.dataDirectory);
            final VelocityCommandManager<CommandSource> manager = new VelocityCommandManager<>(
                    this.container, this.proxy, ExecutionCoordinator.simpleCoordinator(),
                    SenderMapper.identity());
            PackagingChecks.registerCloudCheck(this.logger, manager);
        }
        this.core.start();
    }

    @Subscribe
    public void onProxyShutdown(final ProxyShutdownEvent event) {
        this.core.stop();
    }
}
