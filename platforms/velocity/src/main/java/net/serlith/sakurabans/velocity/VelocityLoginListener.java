package net.serlith.sakurabans.velocity;

import com.velocitypowered.api.event.EventTask;
import com.velocitypowered.api.event.ResultedEvent;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.connection.DisconnectEvent;
import com.velocitypowered.api.event.connection.LoginEvent;
import com.velocitypowered.api.event.player.ServerPostConnectEvent;
import com.velocitypowered.api.event.player.ServerPreConnectEvent;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.proxy.server.RegisteredServer;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.kyori.adventure.text.Component;
import net.serlith.sakurabans.core.enforce.LoginDecision;
import net.serlith.sakurabans.core.enforce.LoginGate;
import net.serlith.sakurabans.core.model.LoginAttempt;
import org.jspecify.annotations.Nullable;

final class VelocityLoginListener {

    private final ProxyServer proxy;
    private final LoginGate gate;
    private final Map<UUID, Component> notices = new ConcurrentHashMap<>();

    VelocityLoginListener(final ProxyServer proxy, final LoginGate gate) {
        this.proxy = proxy;
        this.gate = gate;
    }

    private static LoginAttempt attempt(final Player player, final @Nullable String server) {
        return new LoginAttempt(player.getUniqueId(), player.getUsername(),
                player.getRemoteAddress().getAddress(), server);
    }

    @Subscribe
    @Nullable EventTask onLogin(final LoginEvent event) {
        if (!event.getResult().isAllowed()) {
            return null;
        }
        return EventTask.resumeWhenComplete(this.gate.check(attempt(event.getPlayer(), null))
                .thenAccept(decision -> {
                    if (decision instanceof LoginDecision.Denied denied) {
                        event.setResult(ResultedEvent.ComponentResult.denied(denied.screen()));
                    } else if (decision instanceof LoginDecision.Allowed allowed) {
                        // A banned player let in (S68): told on their first server.
                        allowed.notice().ifPresent(notice -> this.notices.put(
                                event.getPlayer().getUniqueId(), notice));
                    }
                }));
    }

    @Subscribe
    @Nullable EventTask onServerPreConnect(final ServerPreConnectEvent event) {
        if (!event.getResult().isAllowed()) {
            return null;
        }
        final Player player = event.getPlayer();
        final String server = event.getOriginalServer().getServerInfo().getName();
        final boolean initial = player.getCurrentServer().isEmpty();
        return EventTask.resumeWhenComplete(this.gate.checkServer(attempt(player, server), server,
                        initial)
                .thenAccept(decision -> {
                    if (!(decision instanceof LoginDecision.Denied denied)) {
                        return;
                    }
                    if (player.getCurrentServer().isPresent()) {
                        event.setResult(ServerPreConnectEvent.ServerResult.denied());
                        player.sendMessage(denied.screen());
                        return;
                    }
                    final RegisteredServer fallback = denied.fallback()
                            .flatMap(this.proxy::getServer)
                            .orElse(null);
                    if (fallback == null) {
                        event.setResult(ServerPreConnectEvent.ServerResult.denied());
                        player.disconnect(denied.screen());
                        return;
                    }
                    event.setResult(ServerPreConnectEvent.ServerResult.allowed(fallback));
                    // A notice from the login says it better.
                    this.notices.putIfAbsent(player.getUniqueId(), denied.screen());
                }));
    }

    @Subscribe
    void onServerPostConnect(final ServerPostConnectEvent event) {
        final Player player = event.getPlayer();
        player.getCurrentServer().ifPresent(connection -> this.gate.connected(
                attempt(player, connection.getServerInfo().getName())));
        final Component notice = this.notices.remove(event.getPlayer().getUniqueId());
        if (notice != null) {
            event.getPlayer().sendMessage(notice);
        }
        // A proxy can't show chat before the first server, so warnings wait for it.
        if (event.getPreviousServer() == null) {
            this.gate.joined(player.getUniqueId());
        }
    }

    @Subscribe
    void onDisconnect(final DisconnectEvent event) {
        this.notices.remove(event.getPlayer().getUniqueId());
        this.gate.quit(event.getPlayer().getUniqueId());
    }

}
