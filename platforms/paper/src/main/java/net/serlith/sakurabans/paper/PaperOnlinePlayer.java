package net.serlith.sakurabans.paper;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.audience.ForwardingAudience;
import net.kyori.adventure.text.Component;
import net.serlith.sakurabans.core.messaging.ProxyChannel;
import net.serlith.sakurabans.core.platform.OnlinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerKickEvent;

final class PaperOnlinePlayer implements OnlinePlayer, ForwardingAudience.Single {

    private final Player player;
    private final PaperTaskScheduler scheduler;

    PaperOnlinePlayer(final Player player, final PaperTaskScheduler scheduler) {
        this.player = player;
        this.scheduler = scheduler;
    }

    @Override
    public Audience audience() {
        return this.player;
    }

    @Override
    public UUID uuid() {
        return this.player.getUniqueId();
    }

    @Override
    public String name() {
        return this.player.getName();
    }

    @Override
    public InetAddress address() {
        final InetSocketAddress address = this.player.getAddress();
        if (address == null) {
            throw new IllegalStateException(this.player.getName() + " has no address");
        }
        return address.getAddress();
    }

    @Override
    public Optional<String> currentServer() {
        return Optional.empty();
    }

    @Override
    public boolean hasPermission(final String node) {
        return this.player.hasPermission(node);
    }

    @Override
    public Optional<Locale> locale() {
        return Optional.of(this.player.locale());
    }

    @Override
    public void disconnect(final Component reason) {
        this.scheduler.runFor(this.player,
                () -> this.player.kick(reason, PlayerKickEvent.Cause.PLUGIN));
    }

    @Override
    public CompletableFuture<Boolean> connect(final String server) {
        return CompletableFuture.completedFuture(false);
    }

    @Override
    public void sendChannelMessage(final byte[] data) {
        this.scheduler.runFor(this.player, () -> this.player.sendPluginMessage(
                this.scheduler.plugin(), ProxyChannel.NAME, data));
    }
}
