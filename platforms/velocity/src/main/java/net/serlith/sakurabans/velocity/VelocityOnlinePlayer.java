package net.serlith.sakurabans.velocity;

import com.velocitypowered.api.proxy.ConnectionRequestBuilder;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import java.net.InetAddress;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.audience.ForwardingAudience;
import net.kyori.adventure.text.Component;
import net.serlith.sakurabans.core.platform.OnlinePlayer;

final class VelocityOnlinePlayer implements OnlinePlayer, ForwardingAudience.Single {

    private final ProxyServer proxy;
    private final Player player;
    private final VelocityNotices notices;

    VelocityOnlinePlayer(final ProxyServer proxy, final Player player,
            final VelocityNotices notices) {
        this.proxy = proxy;
        this.player = player;
        this.notices = notices;
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

    @Override
    public boolean showNotice(final Component title, final Component body,
            final Component button) {
        return this.notices.show(this.player, title, body, button);
    }

    @Override
    public CompletableFuture<Boolean> connect(final String server) {
        return this.proxy.getServer(server)
                .map(target -> this.player.createConnectionRequest(target).connect()
                        .thenApply(ConnectionRequestBuilder.Result::isSuccessful))
                .orElseGet(() -> CompletableFuture.completedFuture(false));
    }
}
