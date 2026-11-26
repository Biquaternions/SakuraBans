package net.serlith.sakurabans.core.command.module;

import java.util.EnumSet;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.serlith.sakurabans.core.admin.EditorLines;
import net.serlith.sakurabans.core.admin.EditorService;
import net.serlith.sakurabans.core.admin.EditorWatcher;
import net.serlith.sakurabans.core.command.CommandActor;
import net.serlith.sakurabans.core.command.CommandModule;
import net.serlith.sakurabans.core.command.CommandRegistrar;
import net.serlith.sakurabans.core.command.CommandRunner;
import net.serlith.sakurabans.core.command.SakuraCommands;
import net.serlith.sakurabans.core.message.Formats;
import net.serlith.sakurabans.core.message.MessageKey;
import net.serlith.sakurabans.core.permission.Permissions;
import org.incendo.cloud.CommandManager;
import org.incendo.cloud.parser.standard.StringParser;
import org.incendo.cloud.suggestion.SuggestionProvider;
import org.jspecify.annotations.Nullable;

public final class EditorCommands implements CommandModule {

    private final CommandRegistrar registrar;
    private final CommandRunner runner;
    private final EditorService editor;
    private final EditorWatcher watcher;
    private final EditorLines lines;
    private final Formats formats;

    public EditorCommands(final CommandRegistrar registrar, final CommandRunner runner,
            final EditorService editor, final EditorWatcher watcher, final EditorLines lines,
            final Formats formats) {
        this.registrar = registrar;
        this.runner = runner;
        this.editor = editor;
        this.watcher = watcher;
        this.lines = lines;
        this.formats = formats;
    }

    @Override
    public void register(final CommandManager<CommandActor> manager) {
        this.registrar.builder(manager, SakuraCommands.ROOT, MessageKey.COMMAND_DESCRIPTION_EDITOR)
                .ifPresent(root -> manager.command(root.literal("editor")
                        .permission(Permissions.EDITOR)
                        .optional("section", StringParser.stringParser(),
                                SuggestionProvider.blockingStrings((context, input) ->
                                        EditorService.allowed(context.sender()::hasPermission)
                                                .stream().map(EditorService.Section::id)
                                                .toList()))
                        .futureHandler(context -> this.runner.run(context.sender(),
                                () -> this.open(context.sender(),
                                        context.<String>optional("section").orElse(null))))));
        this.registrar.builder(manager, SakuraCommands.ROOT,
                        MessageKey.COMMAND_DESCRIPTION_APPLYEDITS)
                .ifPresent(root -> manager.command(root.literal("applyedits")
                        .permission(Permissions.EDITOR)
                        .required("code", StringParser.stringParser())
                        .futureHandler(context -> this.runner.run(context.sender(),
                                () -> this.apply(context.sender(), context.get("code"))))));
    }

    private CompletableFuture<?> open(final CommandActor actor, final @Nullable String section) {
        if (!this.editor.configured()) {
            this.runner.send(actor, MessageKey.EDITOR_NOT_SET_UP);
            return CompletableFuture.completedFuture(null);
        }
        final Set<EditorService.Section> allowed = EditorService.allowed(actor::hasPermission);
        final Set<EditorService.Section> sections = EnumSet.noneOf(EditorService.Section.class);
        if (section == null) {
            sections.addAll(allowed);
        } else {
            final EditorService.Section chosen = section(section).orElse(null);
            if (chosen == null) {
                this.runner.send(actor, MessageKey.EDITOR_UNKNOWN_SECTION,
                        Placeholder.unparsed("input", section),
                        Placeholder.unparsed("sections", String.join(", ", allowed.stream()
                                .map(EditorService.Section::id).toList())));
                return CompletableFuture.completedFuture(null);
            }
            if (allowed.contains(chosen)) {
                sections.add(chosen);
            }
        }
        if (sections.isEmpty()) {
            this.runner.send(actor, MessageKey.EDITOR_NO_SECTIONS);
            return CompletableFuture.completedFuture(null);
        }
        this.runner.send(actor, MessageKey.EDITOR_OPENING);
        return this.editor.open(actor.asActor(), sections).thenAccept(opened -> {
            switch (opened) {
                case EditorService.Opened.NotConfigured notConfigured ->
                        this.runner.send(actor, MessageKey.EDITOR_NOT_SET_UP);
                case EditorService.Opened.Failed failed ->
                        this.runner.send(actor, EditorLines.problem(failed.problem()));
                case EditorService.Opened.Ready ready -> {
                    final String link = ready.link().toString();
                    this.runner.send(actor, MessageKey.EDITOR_READY,
                            Placeholder.component("link", Component.text(link)
                                    .clickEvent(ClickEvent.openUrl(link))),
                            Placeholder.unparsed("lifetime", this.formats.duration(
                                    ready.lifetime(), this.runner.locale(actor))));
                    if (this.watcher.watch(ready.session(), ready.token(), actor.asOperator(),
                            ready.createdAt())) {
                        this.runner.send(actor, MessageKey.EDITOR_LIVE);
                    }
                }
            }
        });
    }

    private static Optional<EditorService.Section> section(final String text) {
        for (final EditorService.Section section : EditorService.Section.values()) {
            if (section.id().equalsIgnoreCase(text.strip())) {
                return Optional.of(section);
            }
        }
        return Optional.empty();
    }

    private CompletableFuture<?> apply(final CommandActor actor, final String code) {
        if (!this.editor.configured()) {
            this.runner.send(actor, MessageKey.EDITOR_NOT_SET_UP);
            return CompletableFuture.completedFuture(null);
        }
        return this.editor.apply(code.strip(), actor.asActor(),
                EditorService.allowed(actor::hasPermission), EditorService.By.COMMAND)
                .thenAccept(application -> {
                    this.lines.lines(application.applied(), this.runner.locale(actor))
                            .forEach(actor.audience()::sendMessage);
                    if (application.applied() instanceof EditorService.Applied.Done
                            && application.creator() != null
                            && !EditorLines.same(application.creator(), actor.asOperator())) {
                        this.lines.toCreator(application.creator(), application.applied(),
                                MessageKey.EDITOR_APPLIED_BY,
                                Placeholder.unparsed("name", actor.name()));
                    }
                });
    }
}
