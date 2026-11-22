package net.serlith.sakurabans.velocity;

import com.velocitypowered.api.event.EventTask;
import com.velocitypowered.api.event.ResultedEvent;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.connection.DisconnectEvent;
import com.velocitypowered.api.event.connection.LoginEvent;
import com.velocitypowered.api.event.player.ServerPreConnectEvent;
import com.velocitypowered.api.proxy.Player;
import net.serlith.sakurabans.core.enforce.LoginDecision;
import net.serlith.sakurabans.core.enforce.LoginGate;
import net.serlith.sakurabans.core.model.LoginAttempt;
import org.jspecify.annotations.Nullable;

final class VelocityLoginListener {

    private final LoginGate gate;

    VelocityLoginListener(final LoginGate gate) {
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
        return EventTask.resumeWhenComplete(this.gate.checkServer(attempt(player, server), server)
                .thenAccept(decision -> {
                    if (decision instanceof LoginDecision.Denied denied) {
                        event.setResult(ServerPreConnectEvent.ServerResult.denied());
                        if (player.getCurrentServer().isEmpty()) {
                            player.disconnect(denied.screen());
                        } else {
                            player.sendMessage(denied.screen());
                        }
                    }
                }));
    }

    @Subscribe
    void onDisconnect(final DisconnectEvent event) {
        this.gate.quit(event.getPlayer().getUniqueId());
    }

}
