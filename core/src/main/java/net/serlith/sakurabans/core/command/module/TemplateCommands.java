package net.serlith.sakurabans.core.command.module;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.time.InstantSource;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.stream.Collectors;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.serlith.sakurabans.api.template.TemplateInfo;
import net.serlith.sakurabans.core.command.CommandActor;
import net.serlith.sakurabans.core.command.CommandModule;
import net.serlith.sakurabans.core.command.CommandRegistrar;
import net.serlith.sakurabans.core.command.CommandRunner;
import net.serlith.sakurabans.core.command.ConfirmationTracker;
import net.serlith.sakurabans.core.command.SakuraCommands;
import net.serlith.sakurabans.core.configuration.ConfigIssue;
import net.serlith.sakurabans.core.configuration.TemplateFiles;
import net.serlith.sakurabans.core.message.MessageKey;
import net.serlith.sakurabans.core.permission.Permissions;
import net.serlith.sakurabans.core.template.SakuraTemplateService;
import net.serlith.sakurabans.core.util.Durations;
import org.incendo.cloud.CommandManager;
import org.incendo.cloud.context.CommandContext;
import org.incendo.cloud.parser.flag.CommandFlag;

public final class TemplateCommands implements CommandModule {

    static final String IMPORT = "templates import";

    private final CommandRegistrar registrar;
    private final CommandRunner runner;
    private final SakuraTemplateService templates;
    private final TemplateFiles files;
    private final ConfirmationTracker confirmations;
    private final Executor worker;
    private final InstantSource clock;

    public TemplateCommands(final CommandRegistrar registrar, final CommandRunner runner,
            final SakuraTemplateService templates, final TemplateFiles files,
            final ConfirmationTracker confirmations, final Executor worker,
            final InstantSource clock) {
        this.registrar = registrar;
        this.runner = runner;
        this.templates = templates;
        this.files = files;
        this.confirmations = confirmations;
        this.worker = worker;
        this.clock = clock;
    }

    @Override
    public void register(final CommandManager<CommandActor> manager) {
        this.registrar.builder(manager, SakuraCommands.ROOT,
                        MessageKey.COMMAND_DESCRIPTION_TEMPLATES)
                .ifPresent(root -> {
                    final var templates = root.literal("templates")
                            .permission(Permissions.ADMIN_TEMPLATES);
                    manager.command(templates.literal("list")
                            .handler(context -> this.list(context.sender())));
                    manager.command(templates.literal("import")
                            .flag(CommandFlag.builder("confirm").build())
                            .futureHandler(context -> this.runner.run(context.sender(),
                                    () -> this.importFile(context))));
                    manager.command(templates.literal("export")
                            .futureHandler(context -> this.runner.run(context.sender(),
                                    () -> this.export(context.sender()))));
                });
    }

    private void list(final CommandActor actor) {
        final List<TemplateInfo> all = this.templates.templates();
        if (all.isEmpty()) {
            this.runner.send(actor, MessageKey.TEMPLATES_LIST_EMPTY);
            return;
        }
        this.runner.send(actor, MessageKey.TEMPLATES_LIST_HEADER,
                Placeholder.unparsed("count", Integer.toString(all.size())));
        for (final TemplateInfo template : all) {
            this.runner.send(actor, MessageKey.TEMPLATES_LIST_ENTRY,
                    Placeholder.unparsed("name", template.name()),
                    Placeholder.unparsed("kind", template.kind()),
                    Placeholder.unparsed("ladder", ladder(template)),
                    Placeholder.unparsed("reason", template.reason()));
        }
    }

    static String ladder(final TemplateInfo template) {
        return template.ladder().stream()
                .map(step -> step.duration()
                        .map(duration -> Durations.formatCompact(duration).replace(" ", ""))
                        .orElse("permanent"))
                .collect(Collectors.joining(" → "));
    }

    private CompletableFuture<?> importFile(final CommandContext<CommandActor> context) {
        final CommandActor actor = context.sender();
        return CompletableFuture.supplyAsync(this.files::read, this.worker).thenCompose(read -> {
            if (!context.flags().isPresent("confirm")
                    || !this.confirmations.confirm(actor.name(), IMPORT)) {
                this.confirmations.request(actor.name(), IMPORT);
                this.runner.send(actor, MessageKey.TEMPLATES_IMPORT_CONFIRM,
                        Placeholder.unparsed("count", Integer.toString(read.templates().size())));
                return CompletableFuture.completedFuture(null);
            }
            this.errors(actor, read.errors());
            return this.templates.merge(read.templates(), actor.asActor()).thenAccept(report -> {
                this.errors(actor, report.errors());
                this.runner.send(actor, MessageKey.TEMPLATES_IMPORT_DONE,
                        Placeholder.unparsed("created", Integer.toString(report.created())),
                        Placeholder.unparsed("updated", Integer.toString(report.updated())),
                        Placeholder.unparsed("unchanged", Integer.toString(report.unchanged())));
            });
        });
    }

    private void errors(final CommandActor actor, final List<ConfigIssue> errors) {
        errors.forEach(error -> this.runner.send(actor, MessageKey.TEMPLATES_IMPORT_ERROR,
                Placeholder.unparsed("error", error.toString())));
    }

    private CompletableFuture<?> export(final CommandActor actor) {
        final List<TemplateInfo> all = this.templates.templates();
        return CompletableFuture.supplyAsync(() -> {
            try {
                return this.files.export(all, this.clock.instant());
            } catch (final IOException e) {
                throw new UncheckedIOException(e);
            }
        }, this.worker).thenAccept((Path file) -> this.runner.send(actor,
                MessageKey.TEMPLATES_EXPORT_DONE,
                Placeholder.unparsed("count", Integer.toString(all.size())),
                Placeholder.unparsed("file", file.getParent().getFileName() + "/"
                        + file.getFileName())));
    }
}
