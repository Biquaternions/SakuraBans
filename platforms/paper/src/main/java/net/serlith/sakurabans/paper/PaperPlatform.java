package net.serlith.sakurabans.paper;

import java.util.Collection;
import java.util.Optional;
import java.util.UUID;
import net.kyori.adventure.audience.Audience;
import net.serlith.sakurabans.core.platform.OnlinePlayer;
import net.serlith.sakurabans.core.platform.Platform;
import net.serlith.sakurabans.core.platform.ProfileLookup;
import net.serlith.sakurabans.core.platform.ServerIdentity;
import net.serlith.sakurabans.core.platform.TaskScheduler;
import org.bukkit.Server;
import org.bukkit.plugin.Plugin;

final class PaperPlatform implements Platform {

    private final Server server;
    private final PaperTaskScheduler scheduler;
    private final ServerIdentity identity = new PaperServerIdentity();
    private final ProfileLookup profiles;

    PaperPlatform(final Plugin plugin) {
        this.server = plugin.getServer();
        this.scheduler = new PaperTaskScheduler(plugin);
        this.profiles = new PaperProfileLookup(this.server);
    }

    @Override
    public TaskScheduler scheduler() {
        return this.scheduler;
    }

    @Override
    public Optional<OnlinePlayer> player(final UUID uuid) {
        return Optional.ofNullable(this.server.getPlayer(uuid))
                .map(player -> new PaperOnlinePlayer(player, this.scheduler));
    }

    @Override
    public Collection<? extends OnlinePlayer> onlinePlayers() {
        return this.server.getOnlinePlayers().stream()
                .map(player -> new PaperOnlinePlayer(player, this.scheduler))
                .toList();
    }

    @Override
    public Audience console() {
        return this.server.getConsoleSender();
    }

    @Override
    public void dispatchConsoleCommand(final String command) {
        this.scheduler.runGlobal(
                () -> this.server.dispatchCommand(this.server.getConsoleSender(), command));
    }

    @Override
    public ServerIdentity serverIdentity() {
        return this.identity;
    }

    @Override
    public ProfileLookup profiles() {
        return this.profiles;
    }

    @Override
    public boolean onlineMode() {
        // True when this server or the proxy in front of it authenticates with Mojang.
        return this.server.getServerConfig().isProxyOnlineMode();
    }

    @Override
    public String platformVersion() {
        return this.server.getName() + " " + this.server.getVersion();
    }
}
