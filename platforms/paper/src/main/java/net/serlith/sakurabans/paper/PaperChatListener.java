package net.serlith.sakurabans.paper;

import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import net.serlith.sakurabans.core.enforce.ChatDecision;
import net.serlith.sakurabans.core.enforce.ChatGate;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;

final class PaperChatListener implements Listener {

    private final ChatGate gate;
    private final PaperPlatform platform;

    PaperChatListener(final ChatGate gate, final PaperPlatform platform) {
        this.gate = gate;
        this.platform = platform;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    void onChat(final AsyncChatEvent event) {
        final Player player = event.getPlayer();
        final ChatDecision decision = this.gate.checkChat(this.platform.online(player),
                PlainTextComponentSerializer.plainText().serialize(event.message()));
        switch (decision) {
            case ChatDecision.Allowed allowed -> {}
            case ChatDecision.Denied denied -> {
                event.setCancelled(true);
                player.sendMessage(denied.message());
            }
            // Shadow mute: the sender alone still sees it.
            case ChatDecision.ShadowMuted shadow -> event.viewers().removeIf(
                    viewer -> viewer != player);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    void onCommand(final PlayerCommandPreprocessEvent event) {
        final Player player = event.getPlayer();
        final ChatDecision decision =
                this.gate.checkCommand(this.platform.online(player), event.getMessage());
        switch (decision) {
            case ChatDecision.Allowed allowed -> {}
            case ChatDecision.Denied denied -> {
                event.setCancelled(true);
                player.sendMessage(denied.message());
            }
            case ChatDecision.ShadowMuted shadow -> event.setCancelled(true);
        }
    }

}
