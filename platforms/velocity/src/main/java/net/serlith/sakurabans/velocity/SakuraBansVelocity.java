package net.serlith.sakurabans.velocity;

import com.google.inject.Inject;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent;
import com.velocitypowered.api.event.proxy.ProxyShutdownEvent;
import com.velocitypowered.api.plugin.Dependency;
import com.velocitypowered.api.plugin.Plugin;
import net.kyori.adventure.text.logger.slf4j.ComponentLogger;
import net.serlith.sakurabans.core.SakuraBansCore;
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

    private final SakuraBansCore core;

    @Inject
    public SakuraBansVelocity(final ComponentLogger logger) {
        this.core = new SakuraBansCore(logger);
    }

    @Subscribe
    public void onProxyInitialization(final ProxyInitializeEvent event) {
        this.core.start();
    }

    @Subscribe
    public void onProxyShutdown(final ProxyShutdownEvent event) {
        this.core.stop();
    }
}
