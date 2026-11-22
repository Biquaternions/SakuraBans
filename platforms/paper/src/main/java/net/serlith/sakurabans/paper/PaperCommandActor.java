package net.serlith.sakurabans.paper;

import io.papermc.paper.command.brigadier.CommandSourceStack;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import net.kyori.adventure.audience.Audience;
import net.serlith.sakurabans.core.command.CommandActor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.entity.Player;

final class PaperCommandActor implements CommandActor {

    private final CommandSourceStack source;

    PaperCommandActor(final CommandSourceStack source) {
        this.source = source;
    }

    CommandSourceStack source() {
        return this.source;
    }

    private CommandSender sender() {
        return this.source.getSender();
    }

    @Override
    public Audience audience() {
        return this.sender();
    }

    @Override
    public String name() {
        return this.sender().getName();
    }

    @Override
    public Optional<UUID> uuid() {
        return this.sender() instanceof Player player
                ? Optional.of(player.getUniqueId())
                : Optional.empty();
    }

    @Override
    public boolean console() {
        return this.sender() instanceof ConsoleCommandSender;
    }

    @Override
    public boolean hasPermission(final String node) {
        return this.sender().hasPermission(node);
    }

    @Override
    public Optional<Locale> locale() {
        return this.sender() instanceof Player player
                ? Optional.of(player.locale())
                : Optional.empty();
    }

    @Override
    public Optional<String> currentServer() {
        return Optional.empty();
    }
}
