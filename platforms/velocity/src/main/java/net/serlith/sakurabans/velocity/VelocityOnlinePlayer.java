package net.serlith.sakurabans.velocity;

import com.velocitypowered.api.proxy.Player;
import java.net.InetAddress;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.audience.ForwardingAudience;
import net.kyori.adventure.text.Component;
import net.serlith.sakurabans.core.platform.OnlinePlayer;

final class VelocityOnlinePlayer implements OnlinePlayer, ForwardingAudience.Single {

    private final Player player;

    VelocityOnlinePlayer(final Player player) {
        this.player = player;
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
        return this.player.getUsername();
    }

    @Override
    public InetAddress address() {
        return this.player.getRemoteAddress().getAddress();
    }

    @Override
    public Optional<String> currentServer() {
        return this.player.getCurrentServer()
                .map(connection -> connection.getServerInfo().getName());
    }

    @Override
    public boolean hasPermission(final String node) {
        return this.player.hasPermission(node);
    }

    @Override
    public Optional<Locale> locale() {
        return Optional.ofNullable(this.player.getEffectiveLocale());
    }

    @Override
    public void disconnect(final Component reason) {
        this.player.disconnect(reason);
    }
}
