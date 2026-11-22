package net.serlith.sakurabans.core.command;

import java.util.List;
import java.util.Optional;
import net.serlith.sakurabans.core.configuration.ConfigService;
import net.serlith.sakurabans.core.message.MessageKey;
import net.serlith.sakurabans.core.message.MessageService;
import org.incendo.cloud.Command;
import org.incendo.cloud.CommandManager;
import org.incendo.cloud.minecraft.extras.RichDescription;

public final class CommandRegistrar {

    private final ConfigService config;
    private final MessageService messages;

    public CommandRegistrar(final ConfigService config, final MessageService messages) {
        this.config = config;
        this.messages = messages;
    }

    public Optional<Command.Builder<CommandActor>> builder(
            final CommandManager<CommandActor> manager, final String command,
            final MessageKey description) {
        final List<String> labels = this.config.settings().commands().labels(command);
        if (labels.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(manager.commandBuilder(labels.getFirst(),
                labels.subList(1, labels.size()), RichDescription.of(
                        this.messages.render(description, this.messages.defaultLocale())),
                manager.createDefaultCommandMeta()));
    }

    public Optional<String> label(final String command) {
        final List<String> labels = this.config.settings().commands().labels(command);
        return labels.isEmpty() ? Optional.empty() : Optional.of(labels.getFirst());
    }

}
