package net.serlith.sakurabans.fidorial;

import fr.fidorial.entity.Player;
import lombok.RequiredArgsConstructor;
import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.audience.ForwardingAudience;
import net.kyori.adventure.text.Component;
import net.serlith.sakurabans.core.platform.OnlinePlayer;

import java.net.InetAddress;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

@RequiredArgsConstructor
public class FidorialOnlinePlayer implements OnlinePlayer, ForwardingAudience.Single {

    private final Player player;
    private final FidorialTaskScheduler scheduler;

    @Override
    public Audience audience() {
        return this.player;
    }

    @Override
    public UUID uuid() {
        return this.player.uuid();
    }

    @Override
    public String name() {
        return this.player.name();
    }

    @Override
    public InetAddress address() {
        return this.player.address();
    }

    @Override
    public Optional<String> currentServer() {
        return Optional.empty();
    }

    @Override
    public boolean hasPermission(String node) {
        return this.player.hasPermission(node);
    }

    @Override
    public Optional<Locale> locale() {
        return Optional.empty();
    }

    @Override
    public void disconnect(Component reason) {
        this.scheduler.runForPlayer(this.player,
                () -> this.player.kick(reason));
    }

    @Override
    public void disconnectFromNetwork(final Component reason) {
    }

    @Override
    public boolean showNotice(Component title, Component body, Component button) {
        return false;
    }

    @Override
    public CompletableFuture<Boolean> connect(String server) {
        return CompletableFuture.completedFuture(false);
    }

}
