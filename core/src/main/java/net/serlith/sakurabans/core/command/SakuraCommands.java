package net.serlith.sakurabans.core.command;

import java.util.List;
import net.kyori.adventure.text.Component;
import net.serlith.sakurabans.core.message.MessageKey;
import net.serlith.sakurabans.core.message.MessageService;
import net.serlith.sakurabans.core.permission.Permissions;
import org.incendo.cloud.CommandManager;
import org.incendo.cloud.exception.CommandExecutionException;
import org.incendo.cloud.minecraft.extras.MinecraftExceptionHandler;
import org.incendo.cloud.minecraft.extras.MinecraftHelp;
import org.incendo.cloud.minecraft.extras.caption.ComponentCaptionFormatter;
import org.incendo.cloud.parser.standard.StringParser;

public final class SakuraCommands {

    static final String ROOT = "sakurabans";

    private final List<CommandModule> modules;
    private final CommandRegistrar registrar;
    private final CommandRunner runner;
    private final MessageService messages;

    public SakuraCommands(final List<CommandModule> modules, final CommandRegistrar registrar,
            final CommandRunner runner, final MessageService messages) {
        this.modules = List.copyOf(modules);
        this.registrar = registrar;
        this.runner = runner;
        this.messages = messages;
    }

    public void install(final CommandManager<CommandActor> manager) {
        manager.captionRegistry().registerProvider(SakuraCaptions.provider(this.messages));
        MinecraftExceptionHandler.<CommandActor>create(CommandActor::audience)
                .defaultInvalidSyntaxHandler()
                .defaultInvalidSenderHandler()
                .defaultNoPermissionHandler()
                .defaultArgumentParsingHandler()
                .handler(CommandExecutionException.class, (formatter, context) -> {
                    this.runner.internalError(context.context().sender(),
                            context.exception().getCause());
                    return null;
                })
                .captionFormatter(ComponentCaptionFormatter.miniMessage())
                .decorator((formatter, context, message) -> Component.empty()
                        .append(this.runner.render(context.context().sender(), MessageKey.PREFIX))
                        .append(message))
                .registerTo(manager);
        this.help(manager);
        this.modules.forEach(module -> module.register(manager));
    }

    private void help(final CommandManager<CommandActor> manager) {
        this.registrar.label(ROOT).ifPresent(root -> {
            final MinecraftHelp<CommandActor> help =
                    MinecraftHelp.create("/" + root + " help", manager, CommandActor::audience);
            this.registrar.builder(manager, ROOT, MessageKey.COMMAND_DESCRIPTION_SAKURABANS)
                    .ifPresent(builder -> manager.command(builder.literal("help")
                            .permission(Permissions.HELP)
                            .optional("query", StringParser.greedyStringParser())
                            .handler(context -> help.queryCommands(
                                    context.<String>optional("query").orElse(""),
                                    context.sender()))));
        });
    }

}
