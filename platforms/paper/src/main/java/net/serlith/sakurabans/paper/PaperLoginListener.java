package net.serlith.sakurabans.paper;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.kyori.adventure.text.Component;
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
    private final Map<UUID, Component> notices = new ConcurrentHashMap<>();

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
        } else if (decision instanceof LoginDecision.Allowed allowed) {
            // login-check: false let a banned player in: told once they have joined.
            allowed.notice().ifPresent(notice -> this.notices.put(event.getUniqueId(), notice));
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    void onJoin(final PlayerJoinEvent event) {
        final Component notice = this.notices.remove(event.getPlayer().getUniqueId());
        if (notice != null) {
            event.getPlayer().sendMessage(notice);
        }
        this.gate.joined(event.getPlayer().getUniqueId());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    void onQuit(final PlayerQuitEvent event) {
        this.notices.remove(event.getPlayer().getUniqueId());
        this.gate.quit(event.getPlayer().getUniqueId());
    }

}
