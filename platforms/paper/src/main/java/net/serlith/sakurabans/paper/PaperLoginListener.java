package net.serlith.sakurabans.paper;

import net.serlith.sakurabans.core.enforce.LoginDecision;
import net.serlith.sakurabans.core.enforce.LoginGate;
import net.serlith.sakurabans.core.model.LoginAttempt;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

final class PaperLoginListener implements Listener {

    private final LoginGate gate;

    PaperLoginListener(final LoginGate gate) {
        this.gate = gate;
    }

    @EventHandler(priority = EventPriority.NORMAL)
    void onPreLogin(final AsyncPlayerPreLoginEvent event) {
        if (event.getLoginResult() != AsyncPlayerPreLoginEvent.Result.ALLOWED) {
            return;
        }
        final LoginDecision decision = this.gate.check(new LoginAttempt(event.getUniqueId(),
                event.getName(), event.getAddress(), null)).join();
        if (decision instanceof LoginDecision.Denied denied) {
            event.disallow(AsyncPlayerPreLoginEvent.Result.KICK_BANNED, denied.screen());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    void onJoin(final PlayerJoinEvent event) {
        this.gate.joined(event.getPlayer().getUniqueId());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    void onQuit(final PlayerQuitEvent event) {
        this.gate.quit(event.getPlayer().getUniqueId());
    }

}
