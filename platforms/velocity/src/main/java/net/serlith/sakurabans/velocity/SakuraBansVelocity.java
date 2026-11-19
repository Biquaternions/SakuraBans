package net.serlith.sakurabans.velocity;

import com.google.inject.Inject;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent;
import com.velocitypowered.api.event.proxy.ProxyShutdownEvent;
import com.velocitypowered.api.plugin.Dependency;
import com.velocitypowered.api.plugin.Plugin;
import com.velocitypowered.api.plugin.annotation.DataDirectory;
import com.velocitypowered.api.proxy.ProxyServer;
import java.nio.file.Path;
import net.kyori.adventure.text.logger.slf4j.ComponentLogger;
import net.serlith.sakurabans.core.SakuraBansCore;
import net.serlith.sakurabans.core.bootstrap.CoreBootstrap;
import net.serlith.sakurabans.core.bootstrap.CoreEnvironment;
import net.serlith.sakurabans.core.platform.PlatformType;
import net.serlith.sakurabans.core.util.BuildConstants;

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
    private final ComponentLogger logger;
    private final SakuraBansCore core;

    @Inject
    public SakuraBansVelocity(final ProxyServer proxy, final ComponentLogger logger,
            @DataDirectory final Path dataDirectory) {
        this.proxy = proxy;
        this.logger = logger;
        this.core = CoreBootstrap.create(
                new CoreEnvironment(dataDirectory, logger, PlatformType.VELOCITY));
    }

    @Subscribe
    public void onProxyInitialization(final ProxyInitializeEvent event) {
        this.core.start(new VelocityPlatform(this.proxy, this.logger));
    }

    @Subscribe
    public void onProxyShutdown(final ProxyShutdownEvent event) {
        this.core.stop();
    }
}
