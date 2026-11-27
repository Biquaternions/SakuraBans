package net.serlith.sakurabans.core.command.module;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.time.InstantSource;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.stream.Collectors;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import net.serlith.sakurabans.api.model.AddressVictim;
import net.serlith.sakurabans.api.model.GlobalScope;
import net.serlith.sakurabans.api.model.PlayerVictim;
import net.serlith.sakurabans.api.model.Scope;
import net.serlith.sakurabans.api.model.Victim;
import net.serlith.sakurabans.api.template.LadderTemplate;
import net.serlith.sakurabans.api.template.PresetTemplate;
import net.serlith.sakurabans.api.template.TemplateInfo;
import net.serlith.sakurabans.core.command.CommandActor;
import net.serlith.sakurabans.core.command.CommandModule;
import net.serlith.sakurabans.core.command.CommandRegistrar;
import net.serlith.sakurabans.core.command.CommandRunner;
import net.serlith.sakurabans.core.command.ConfirmationTracker;
import net.serlith.sakurabans.core.command.SakuraCommands;
import net.serlith.sakurabans.core.command.parser.ScopeParser;
import net.serlith.sakurabans.core.command.parser.TargetParser;
import net.serlith.sakurabans.core.configuration.ConfigIssue;
import net.serlith.sakurabans.core.configuration.TemplateFiles;
import net.serlith.sakurabans.core.message.MessageKey;
import net.serlith.sakurabans.core.message.Placeholders;
import net.serlith.sakurabans.core.model.AdminResult;
import net.serlith.sakurabans.core.model.ResolvedTarget;
import net.serlith.sakurabans.core.model.StoredTemplate;
import net.serlith.sakurabans.core.model.TargetInput;
import net.serlith.sakurabans.core.model.TemplateSteps;
import net.serlith.sakurabans.core.permission.Permissions;
import net.serlith.sakurabans.core.player.TargetResolver;
import net.serlith.sakurabans.core.storage.repository.TemplateRepository.ResetCount;
import net.serlith.sakurabans.core.template.SakuraTemplateService;
import net.serlith.sakurabans.core.util.Scopes;
import org.incendo.cloud.CommandManager;
import org.incendo.cloud.context.CommandContext;
import org.incendo.cloud.parser.flag.CommandFlag;
import org.incendo.cloud.parser.standard.StringParser;
import org.incendo.cloud.suggestion.SuggestionProvider;

public final class TemplateCommands implements CommandModule {

    static final String IMPORT = "templates import";
    private static final String SCOPE = "scope";
    private static final String ALL = "all";

    private final CommandRegistrar registrar;
    private final CommandRunner runner;
    private final SakuraTemplateService templates;
    private final TemplateFiles files;
    private final ConfirmationTracker confirmations;
    private final Executor worker;
    private final InstantSource clock;
    private final TargetParser targetParser;
    private final TargetResolver targets;
    private final ScopeParser scopes;

    public TemplateCommands(final CommandRegistrar registrar, final CommandRunner runner,
            final SakuraTemplateService templates, final TemplateFiles files,
            final ConfirmationTracker confirmations, final Executor worker,
            final InstantSource clock, final TargetParser targetParser,
            final TargetResolver targets, final ScopeParser scopes) {
        this.registrar = registrar;
        this.runner = runner;
        this.templates = templates;
        this.files = files;
        this.confirmations = confirmations;
        this.worker = worker;
        this.clock = clock;
        this.targetParser = targetParser;
        this.targets = targets;
        this.scopes = scopes;
    }

    @Override
    public void register(final CommandManager<CommandActor> manager) {
        this.registrar.builder(manager, SakuraCommands.ROOT,
                        MessageKey.COMMAND_DESCRIPTION_TEMPLATES)
                .ifPresent(root -> {
                    final var templates = root.literal("templates")
                            .permission(Permissions.ADMIN_TEMPLATES);
                    // A scope names the variants there; none means global.
                    manager.command(templates.literal("list")
                            .optional(SCOPE, this.scopes.configDescriptor())
                            .handler(context -> this.list(context.sender(),
                                    context.<Scope>optional(SCOPE)
                                            .orElse(GlobalScope.INSTANCE))));
                    manager.command(templates.literal("import")
                            .optional(SCOPE, this.scopes.configDescriptor())
                            .flag(CommandFlag.builder(ALL).build())
                            .flag(CommandFlag.builder("confirm").build())
                            .futureHandler(context -> this.runner.run(context.sender(),
                                    () -> this.importFile(context))));
                    manager.command(templates.literal("export")
                            .optional(SCOPE, this.scopes.configDescriptor())
                            .flag(CommandFlag.builder(ALL).build())
                            .futureHandler(context -> this.runner.run(context.sender(),
                                    () -> this.export(context))));
                    manager.command(templates.literal("reset")
                            .required("target", this.targetParser.descriptor())
                            // Flag-yielding, so "reset Steve --confirm" names no template.
                            .optional("template",
                                    StringParser.greedyFlagYieldingStringParser(),
                                    SuggestionProvider.blockingStrings((context, input) ->
                                            this.templates.templates().stream()
                                                    .map(TemplateInfo::name).toList()))
                            .flag(CommandFlag.builder("confirm").build())
                            .futureHandler(context -> this.runner.run(context.sender(),
                                    () -> this.reset(context))));
                });
    }

    private void list(final CommandActor actor, final Scope scope) {
        final boolean global = scope instanceof GlobalScope;
        final List<StoredTemplate> all = this.templates.definitionsAt(scope);
        final TagResolver where = Placeholder.unparsed("scope", Scopes.format(scope));
        if (all.isEmpty()) {
            this.runner.send(actor, global ? MessageKey.TEMPLATES_LIST_EMPTY
                    : MessageKey.TEMPLATES_LIST_EMPTY_SCOPE, where);
            return;
        }
        this.runner.send(actor, global ? MessageKey.TEMPLATES_LIST_HEADER
                        : MessageKey.TEMPLATES_LIST_HEADER_SCOPE, where,
                Placeholder.unparsed("count", Integer.toString(all.size())));
        for (final StoredTemplate definition : all) {
            final TemplateInfo template = definition.template();
            this.runner.send(actor, MessageKey.TEMPLATES_LIST_ENTRY,
                    Placeholder.unparsed("name", template.name()),
                    Placeholder.component("type", this.runner.render(actor,
                            template instanceof PresetTemplate preset
                                    ? Placeholders.typeKey(preset.type())
                                    : MessageKey.TEMPLATES_LIST_LADDER)),
                    Placeholder.unparsed("modifiers", modifiers(template)),
                    Placeholder.unparsed("ladder", ladder(template)),
                    Placeholder.unparsed("reason", template.reason()));
            final Map<Scope, StoredTemplate> variants = global
                    ? this.templates.variants(template.name()) : Map.of();
            if (!variants.isEmpty()) {
                this.runner.send(actor, MessageKey.TEMPLATES_LIST_VARIANTS,
                        Placeholder.unparsed("scopes", String.join(", ", variants.keySet()
                                .stream().map(Scopes::format).toList())));
            }
        }
    }

    static String modifiers(final TemplateInfo template) {
        final List<String> modifiers = new ArrayList<>();
        if (template.ip()) {
            modifiers.add("ip");
        }
        if (template.shadow()) {
            modifiers.add("shadow");
        }
        template.scope().ifPresent(scope -> modifiers.add("scope=" + Scopes.format(scope)));
        return modifiers.isEmpty() ? "" : " [" + String.join(" ", modifiers) + "]";
    }

    /** A ladder's steps, or a preset's duration (empty when the command decides). */
    static String ladder(final TemplateInfo template) {
        return switch (template) {
            case PresetTemplate preset -> TemplateSteps.format(preset.duration()).orElse("");
            case LadderTemplate ladder -> ladder.steps().stream()
                    .map(TemplateSteps::format)
                    .collect(Collectors.joining(" → "));
        };
    }

    /** The file's global templates, one scope's variants, or with {@code --all} everything. */
    private CompletableFuture<?> importFile(final CommandContext<CommandActor> context) {
        final CommandActor actor = context.sender();
        final boolean everything = context.flags().isPresent(ALL);
        final Scope scope = context.<Scope>optional(SCOPE).orElse(GlobalScope.INSTANCE);
        final String command = IMPORT + " " + (everything ? ALL : Scopes.format(scope));
        return CompletableFuture.supplyAsync(this.files::read, this.worker).thenCompose(read -> {
            final List<TemplateInfo> chosen = read.templates().stream()
                    .filter(definition -> everything || Objects.equals(
                            definition.variant().orElse(null),
                            scope instanceof GlobalScope ? null : scope))
                    .toList();
            if (!context.flags().isPresent("confirm")
                    || !this.confirmations.confirm(actor.name(), command)) {
                this.confirmations.request(actor.name(), command);
                this.runner.send(actor, MessageKey.TEMPLATES_IMPORT_CONFIRM,
                        Placeholder.unparsed("count", Integer.toString(chosen.size())));
                return CompletableFuture.completedFuture(null);
            }
            this.errors(actor, read.errors());
            return this.templates.merge(chosen, actor.asActor()).thenAccept(report -> {
                this.errors(actor, report.errors());
                this.runner.send(actor, MessageKey.TEMPLATES_IMPORT_DONE,
                        Placeholder.unparsed("created", Integer.toString(report.created())),
                        Placeholder.unparsed("updated", Integer.toString(report.updated())),
                        Placeholder.unparsed("unchanged", Integer.toString(report.unchanged())));
            });
        });
    }

    /**
     * Forgets ladder progress: the uses one template (or every template) counts for
     * a player or an address, their alts' and addresses' too for an {@code address-shared}
     * ladder. The confirmation says how many uses of how many victims would go.
     */
    private CompletableFuture<?> reset(final CommandContext<CommandActor> context) {
        final CommandActor actor = context.sender();
        final TargetInput input = context.get("target");
        final String template = context.<String>optional("template")
                .map(name -> name.strip().toLowerCase(Locale.ROOT))
                .filter(name -> !name.isEmpty()).orElse(null);
        return this.targets.resolve(input).thenCompose(target -> {
            final Victim victim = switch (target) {
                case ResolvedTarget.Player player -> PlayerVictim.of(player.uuid());
                case ResolvedTarget.Address address -> AddressVictim.of(address.address());
                case ResolvedTarget.PunishmentId id -> {
                    this.runner.send(actor, MessageKey.COMMAND_ID_NOT_ALLOWED);
                    yield null;
                }
                case ResolvedTarget.Unknown unknown -> {
                    this.runner.send(actor, MessageKey.COMMAND_UNKNOWN_TARGET,
                            Placeholder.unparsed("input",
                                    PunishCommands.inputText(unknown.input())));
                    yield null;
                }
                case ResolvedTarget.Range range -> {
                    this.runner.send(actor, MessageKey.TEMPLATES_RESET_TARGET,
                            Placeholder.unparsed("input", PunishCommands.inputText(input)));
                    yield null;
                }
            };
            if (victim == null) {
                return CompletableFuture.completedFuture(null);
            }
            final String shown = target instanceof ResolvedTarget.Player player
                    ? PunishCommands.displayName(player) : PunishCommands.inputText(input);
            final TagResolver what = template == null
                    ? Placeholder.component("what", this.runner.render(actor,
                            MessageKey.TEMPLATES_RESET_ALL))
                    : Placeholder.unparsed("what", template);
            final String command = "templates reset " + (victim instanceof PlayerVictim player
                    ? player.uuid().toString() : PunishCommands.inputText(input)) + " "
                    + (template == null ? "*" : template);
            final boolean confirmed = context.flags().isPresent("confirm")
                    && this.confirmations.confirm(actor.name(), command);
            return (confirmed
                    ? this.templates.resetUses(victim, template, actor.asActor())
                    : this.templates.resetCount(victim, template, actor.asActor()))
                    .thenAccept(result -> {
                        if (!(result instanceof AdminResult.Ok<ResetCount> ok)) {
                            this.runner.send(actor, MessageKey.PUNISH_FAILURE_FORBIDDEN);
                            return;
                        }
                        if (!confirmed) {
                            this.confirmations.request(actor.name(), command);
                        }
                        this.runner.send(actor, confirmed ? MessageKey.TEMPLATES_RESET_DONE
                                        : MessageKey.TEMPLATES_RESET_CONFIRM, what,
                                Placeholder.unparsed("target", shown),
                                Placeholder.unparsed("count", Integer.toString(ok.value().uses())),
                                Placeholder.unparsed("victims",
                                        Integer.toString(ok.value().victims())));
                    });
        });
    }

    private void errors(final CommandActor actor, final List<ConfigIssue> errors) {
        errors.forEach(error -> this.runner.send(actor, MessageKey.TEMPLATES_IMPORT_ERROR,
                Placeholder.unparsed("error", error.toString())));
    }

    /** The global templates, one scope's variants, or with {@code --all} everything. */
    private CompletableFuture<?> export(final CommandContext<CommandActor> context) {
        final CommandActor actor = context.sender();
        final List<TemplateInfo> all = (context.flags().isPresent(ALL)
                ? this.templates.definitions()
                : this.templates.definitionsAt(context.<Scope>optional(SCOPE)
                        .orElse(GlobalScope.INSTANCE)))
                .stream().map(StoredTemplate::template).toList();
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
