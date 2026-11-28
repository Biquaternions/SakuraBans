package net.serlith.sakurabans.velocity;

import com.google.inject.Inject;
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
import net.serlith.sakurabans.core.bootstrap.CoreBootstrap;
import net.serlith.sakurabans.core.bootstrap.CoreEnvironment;
import net.serlith.sakurabans.core.command.CommandActor;
import net.serlith.sakurabans.core.enforce.SignedChatPolicy;
import net.serlith.sakurabans.core.placeholder.MiniPlaceholdersBridge;
import net.serlith.sakurabans.core.util.BuildConstants;
import org.incendo.cloud.execution.ExecutionCoordinator;
import org.incendo.cloud.velocity.VelocityCommandManager;

@Plugin(
        id = "sakurabans",
        name = "SakuraBans",
        version = BuildConstants.VERSION,
        authors = {"Biquaternions"},
        dependencies = {
            @Dependency(id = "luckperms", optional = true),
            @Dependency(id = "floodgate", optional = true),
            @Dependency(id = SakuraBansVelocity.SIGNED_VELOCITY, optional = true),
            @Dependency(id = SakuraBansVelocity.PACKET_EVENTS, optional = true),
            @Dependency(id = SakuraBansVelocity.MINI_PLACEHOLDERS, optional = true)
        }
)
public final class SakuraBansVelocity {

    static final String SIGNED_VELOCITY = "signedvelocity";
    static final String PACKET_EVENTS = "packetevents";
    static final String MINI_PLACEHOLDERS = "miniplaceholders";

    private final ProxyServer proxy;
    private final ComponentLogger logger;
    private final PluginContainer container;
    private final SakuraBansCore core;

    @Inject
    public SakuraBansVelocity(final ProxyServer proxy, final ComponentLogger logger,
            @DataDirectory final Path dataDirectory, final PluginContainer container) {
        this.proxy = proxy;
        this.logger = logger;
        this.container = container;
        this.core = CoreBootstrap.create(
                new CoreEnvironment(dataDirectory, logger, true, System.getenv()));
    }

    @Subscribe
    public void onProxyInitialization(final ProxyInitializeEvent event) {
        final VelocityCommandManager<CommandActor> commands = new VelocityCommandManager<>(
                this.container, this.proxy, ExecutionCoordinator.simpleCoordinator(),
                new VelocitySenderMapper());
        this.core.commands().install(commands);
        final VelocityPlatform platform = new VelocityPlatform(this.proxy, this.logger,
                VelocityNotices.create(
                        this.proxy.getPluginManager().isLoaded(PACKET_EVENTS), this.logger));
        this.core.start(platform);
        final SignedChatPolicy signedChat = new SignedChatPolicy(
                this.proxy.getPluginManager().isLoaded(SIGNED_VELOCITY));
        signedChat.warnIfMissing(this.logger);
        this.proxy.getEventManager().register(this,
                new VelocityLoginListener(this.proxy, this.core.loginGate()));
        this.proxy.getEventManager().register(this,
                new VelocityChatListener(this.core.chatGate(), platform, signedChat));
        // A lambda, so the bridge class is resolved only when MiniPlaceholders is there.
        this.core.placeholderBridges().start("MiniPlaceholders",
                this.proxy.getPluginManager().isLoaded(MINI_PLACEHOLDERS),
                () -> MiniPlaceholdersBridge.register(this.core.placeholders()));
    }

    @Subscribe
    public void onProxyShutdown(final ProxyShutdownEvent event) {
        this.core.stop();
    }
}
