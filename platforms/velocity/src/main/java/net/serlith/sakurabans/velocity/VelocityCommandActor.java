package net.serlith.sakurabans.velocity;

import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.proxy.ConsoleCommandSource;
import com.velocitypowered.api.proxy.Player;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import net.kyori.adventure.audience.Audience;
import net.serlith.sakurabans.core.command.CommandActor;

final class VelocityCommandActor implements CommandActor {

    private final CommandSource source;

    VelocityCommandActor(final CommandSource source) {
        this.source = source;
    }

    CommandSource source() {
        return this.source;
    }

    @Override
    public Audience audience() {
        return this.source;
    }

    @Override
    public String name() {
        return this.source instanceof Player player ? player.getUsername() : "CONSOLE";
    }

    @Override
    public Optional<UUID> uuid() {
        return this.source instanceof Player player
                ? Optional.of(player.getUniqueId())
                : Optional.empty();
    }

    @Override
    public boolean console() {
        return this.source instanceof ConsoleCommandSource;
    }

    @Override
    public boolean hasPermission(final String node) {
        return this.source.hasPermission(node);
    }

    @Override
    public Optional<Locale> locale() {
        return this.source instanceof Player player
                ? Optional.ofNullable(player.getEffectiveLocale())
                : Optional.empty();
    }

    @Override
    public Optional<String> currentServer() {
        return this.source instanceof Player player
                ? player.getCurrentServer().map(server -> server.getServerInfo().getName())
                : Optional.empty();
    }
}
