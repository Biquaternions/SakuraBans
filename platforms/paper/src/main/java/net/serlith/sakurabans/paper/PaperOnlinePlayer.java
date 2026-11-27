package net.serlith.sakurabans.paper;

import io.papermc.paper.dialog.Dialog;
import io.papermc.paper.registry.data.dialog.ActionButton;
import io.papermc.paper.registry.data.dialog.DialogBase;
import io.papermc.paper.registry.data.dialog.body.DialogBody;
import io.papermc.paper.registry.data.dialog.type.DialogType;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.audience.ForwardingAudience;
import net.kyori.adventure.text.Component;
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
    public void disconnectFromNetwork(final Component reason) {
        this.scheduler.runFor(this.player, () -> {
            if (!this.player.isOnline()) {
                return;
            }
            // Without the channel no proxy would read it: kick from here.
            if (!this.player.getListeningPluginChannels().contains(PaperNetworkKick.CHANNEL)) {
                this.player.kick(reason, PlayerKickEvent.Cause.PLUGIN);
                return;
            }
            // Raw first (the whole screen on Velocity); the legacy one for a proxy without it.
            this.player.sendPluginMessage(this.scheduler.plugin(), PaperNetworkKick.CHANNEL,
                    PaperNetworkKick.raw(this.player.getName(), reason));
            this.player.sendPluginMessage(this.scheduler.plugin(), PaperNetworkKick.CHANNEL,
                    PaperNetworkKick.legacy(this.player.getName(), reason));
            // Still here: no proxy answered (bungee-plugin-message-channel off, or none).
            this.scheduler.runLater(this.player, PaperNetworkKick.FALLBACK_TICKS, () -> {
                if (this.player.isOnline()) {
                    this.player.kick(reason, PlayerKickEvent.Cause.PLUGIN);
                }
            });
        });
    }

    @Override
    public boolean showNotice(final Component title, final Component body,
            final Component button) {
        // Built and shown on the player's thread (Folia: its region). False when the hop isn't
        // scheduled (S64): the player is offline, or their scheduler retired. A player who
        // leaves after it was scheduled still drops it silently.
        if (!this.player.isOnline()) {
            return false;
        }
        return this.scheduler.runFor(this.player, () -> this.player.showDialog(Dialog.create(
                factory -> factory.empty()
                        .base(DialogBase.builder(title)
                                .body(List.of(DialogBody.plainMessage(body)))
                                .build())
                        .type(DialogType.notice(ActionButton.builder(button).build())))));
    }

    @Override
    public CompletableFuture<Boolean> connect(final String server) {
        return CompletableFuture.completedFuture(false);
    }
}
