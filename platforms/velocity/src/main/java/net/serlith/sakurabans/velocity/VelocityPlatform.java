package net.serlith.sakurabans.velocity;

import com.velocitypowered.api.proxy.ProxyServer;
import java.util.Collection;
import java.util.Optional;
import java.util.UUID;
import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.text.logger.slf4j.ComponentLogger;
import net.serlith.sakurabans.core.platform.OnlinePlayer;
import net.serlith.sakurabans.core.platform.Platform;
import net.serlith.sakurabans.core.platform.ProfileLookup;
import net.serlith.sakurabans.core.platform.ServerIdentity;
import net.serlith.sakurabans.core.platform.TaskScheduler;
import net.serlith.sakurabans.core.util.Futures;

final class VelocityPlatform implements Platform {

    private final ProxyServer proxy;
    private final ComponentLogger logger;
    private final TaskScheduler scheduler;
    private final ServerIdentity identity;

    VelocityPlatform(final ProxyServer proxy, final ComponentLogger logger) {
        this.proxy = proxy;
        this.logger = logger;
        this.scheduler = new VelocityTaskScheduler(proxy);
        this.identity = new VelocityServerIdentity(proxy);
    }

    @Override
    public TaskScheduler scheduler() {
        return this.scheduler;
    }

    @Override
    public Optional<OnlinePlayer> player(final UUID uuid) {
        return this.proxy.getPlayer(uuid).map(VelocityOnlinePlayer::new);
    }

    @Override
    public Collection<? extends OnlinePlayer> onlinePlayers() {
        return this.proxy.getAllPlayers().stream().map(VelocityOnlinePlayer::new).toList();
    }

    @Override
    public Audience console() {
        return this.proxy.getConsoleCommandSource();
    }

    @Override
    public void dispatchConsoleCommand(final String command) {
        Futures.logged(this.proxy.getCommandManager()
                .executeAsync(this.proxy.getConsoleCommandSource(), command),
                this.logger, "run the console command '" + command + "'");
    }

    @Override
    public ServerIdentity serverIdentity() {
        return this.identity;
    }

    /** Velocity has no profile lookup: a proxy resolves only names it has seen (plan 6.2). */
    @Override
    public ProfileLookup profiles() {
        return ProfileLookup.NONE;
    }

    @Override
    public boolean onlineMode() {
        return this.proxy.getConfiguration().isOnlineMode();
    }

    @Override
    public String platformVersion() {
        return this.proxy.getVersion().getName() + " " + this.proxy.getVersion().getVersion();
    }
}
