package net.serlith.sakurabans.core.command.module;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.serlith.sakurabans.api.model.Actor;
import net.serlith.sakurabans.api.model.EvidenceRef;
import net.serlith.sakurabans.api.model.Scope;
import net.serlith.sakurabans.api.model.Victim;
import net.serlith.sakurabans.api.punish.LadderRequest;
import net.serlith.sakurabans.api.punish.LadderResult;
import net.serlith.sakurabans.api.template.LadderTemplate;
import net.serlith.sakurabans.api.template.PresetTemplate;
import net.serlith.sakurabans.api.template.TemplateInfo;
import net.serlith.sakurabans.api.validation.ValidationException;
import net.serlith.sakurabans.core.command.CommandActor;
import net.serlith.sakurabans.core.command.CommandModule;
import net.serlith.sakurabans.core.command.CommandRegistrar;
import net.serlith.sakurabans.core.command.CommandRunner;
import net.serlith.sakurabans.core.command.PunishFlags;
import net.serlith.sakurabans.core.command.Replies;
import net.serlith.sakurabans.core.command.ScopeAccess;
import net.serlith.sakurabans.core.command.parser.TargetParser;
import net.serlith.sakurabans.core.message.MessageKey;
import net.serlith.sakurabans.core.message.Placeholders;
import net.serlith.sakurabans.core.model.ResolvedTarget;
import net.serlith.sakurabans.core.model.SakuraPunishResult;
import net.serlith.sakurabans.core.model.StoredTemplate;
import net.serlith.sakurabans.core.model.TargetInput;
import net.serlith.sakurabans.core.permission.Permissions;
import net.serlith.sakurabans.core.player.NameCache;
import net.serlith.sakurabans.core.player.TargetResolver;
import net.serlith.sakurabans.core.punish.SakuraPunishmentService;
import net.serlith.sakurabans.core.scope.SakuraScopeService;
import net.serlith.sakurabans.core.template.SakuraTemplateService;
import org.incendo.cloud.Command;
import org.incendo.cloud.CommandManager;
import org.incendo.cloud.context.CommandContext;
import org.incendo.cloud.parser.flag.CommandFlag;
import org.incendo.cloud.parser.flag.FlagContext;
import org.incendo.cloud.parser.standard.StringParser;
import org.incendo.cloud.suggestion.SuggestionProvider;

/** {@code /punish <target> <ladder> [flags…] [-r reason]} (D37; its own module since S64). */
public final class LadderCommands implements CommandModule {

    static final String PUNISH = "punish";

    private final CommandRegistrar registrar;
    private final CommandRunner runner;
    private final PunishFlags flags;
    private final TargetParser targetParser;
    private final TargetResolver targets;
    private final SakuraPunishmentService punishments;
    private final SakuraTemplateService templates;
    private final PunishSupport support;

    public LadderCommands(final CommandRegistrar registrar, final CommandRunner runner,
            final Replies replies, final PunishFlags flags, final TargetParser targetParser,
            final TargetResolver targets, final SakuraPunishmentService punishments,
            final NameCache names, final SakuraScopeService scopes,
            final ScopeAccess scopeAccess, final SakuraTemplateService templates) {
        this.registrar = registrar;
        this.runner = runner;
        this.flags = flags;
        this.targetParser = targetParser;
        this.targets = targets;
        this.punishments = punishments;
        this.templates = templates;
        this.support = new PunishSupport(registrar, runner, replies, names, scopes, scopeAccess);
    }

    @Override
    public void register(final CommandManager<CommandActor> manager) {
        this.registrar.builder(manager, PUNISH, MessageKey.COMMAND_DESCRIPTION_PUNISH)
                .map(this::build)
                .ifPresent(manager::command);
    }

    private Command.Builder<CommandActor> build(final Command.Builder<CommandActor> start) {
        Command.Builder<CommandActor> builder = start
                .permission(Permissions.PUNISH)
                .required("target", this.targetParser.descriptor())
                .required("ladder", StringParser.stringParser(), this.ladderNames());
        for (final CommandFlag<?> flag : List.of(
                this.flags.silent(Permissions.punishFlag(PunishFlags.SILENT)),
                this.flags.hide(Permissions.punishFlag(PunishFlags.HIDE)),
                this.flags.global(),
                this.flags.server(),
                this.flags.category(),
                this.flags.scope(),
                this.flags.sender(),
                this.flags.senderUuid(),
                this.flags.skip(Permissions.punishFlag(PunishFlags.SKIP)),
                this.flags.evidence(Permissions.punishFlag(PunishFlags.EVIDENCE)),
                this.flags.ip(Permissions.punishFlag(PunishFlags.IP)),
                this.flags.override(Permissions.punishFlag(PunishFlags.OVERRIDE)),
                this.flags.ifLonger(Permissions.punishFlag(PunishFlags.OVERRIDE)),
                this.flags.reason())) {
            builder = builder.flag(flag);
        }
        return builder.futureHandler(context -> this.runner.run(context.sender(),
                () -> this.execute(context)));
    }

    private CompletableFuture<?> execute(final CommandContext<CommandActor> context) {
        final CommandActor actor = context.sender();
        final FlagContext flags = context.flags();
        final TargetInput input = context.get("target");
        final String name = context.<String>get("ladder").strip().toLowerCase(Locale.ROOT);
        final Optional<Scope> scope = PunishSupport.scope(flags);
        final Actor issuer = PunishSupport.issuer(actor, flags);
        if (issuer == null) {
            this.runner.send(actor, MessageKey.COMMAND_SENDER_NEEDS_NAME);
            return CompletableFuture.completedFuture(null);
        }
        final TemplateInfo template = this.templates.resolve(name, scope.orElse(null), issuer)
                .map(StoredTemplate::template).orElse(null);
        if (template == null) {
            this.runner.send(actor, MessageKey.PUNISH_FAILURE_NO_LADDER,
                    Placeholder.unparsed("name", name));
            return CompletableFuture.completedFuture(null);
        }
        if (template instanceof PresetTemplate preset) {
            this.runner.send(actor, MessageKey.PUNISH_FAILURE_NOT_A_LADDER,
                    Placeholder.unparsed("name", name),
                    Placeholder.unparsed("player", PunishCommands.inputText(input)),
                    this.support.command(PunishSupport.plain(preset.type())),
                    Placeholder.component("type", this.runner.render(actor,
                            Placeholders.typeKey(preset.type()))));
            return CompletableFuture.completedFuture(null);
        }
        if (template.permissionRequired()
                && !actor.hasPermission(Permissions.template(template.name()))) {
            this.runner.send(actor, MessageKey.PUNISH_FAILURE_TEMPLATE_NO_PERMISSION,
                    Placeholder.unparsed("name", template.name()));
            return CompletableFuture.completedFuture(null);
        }
        if (!this.support.scopeAllowed(actor, scope.orElse(null), template)) {
            return CompletableFuture.completedFuture(null);
        }
        final List<EvidenceRef> evidence = this.support.evidence(actor, flags);
        if (evidence == null) {
            return CompletableFuture.completedFuture(null);
        }
        final String reason = PunishSupport.reason(flags);
        // A composite victim only with -I: a step may be a kick.
        final boolean address = flags.isPresent(PunishFlags.IP);
        return this.targets.resolve(input).thenCompose(target -> {
            final Victim victim = this.support.victim(actor,
                    Permissions.punishFlag(PunishFlags.IP), Permissions.punishFlag("range"),
                    target, address);
            if (victim == null) {
                return CompletableFuture.completedFuture(null);
            }
            final LadderRequest request;
            try {
                request = LadderRequest.builder(template.name(), victim, issuer)
                        .scope(scope.orElse(null))
                        .reason(reason)
                        .evidence(evidence)
                        .build();
            } catch (final ValidationException e) {
                this.support.invalid(actor, e);
                return CompletableFuture.completedFuture(null);
            }
            final String shown = target instanceof ResolvedTarget.Player player
                    ? PunishCommands.displayName(player) : PunishCommands.inputText(input);
            return this.punishments.applyLadder(request, PunishSupport.options(flags))
                    .thenAccept(result -> this.reply(actor, template.name(), shown, result))
                    .exceptionally(failure -> this.support.invalidOrRethrow(actor, failure));
        });
    }

    private void reply(final CommandActor actor, final String ladder, final String victim,
            final LadderResult result) {
        switch (result) {
            case LadderResult.Punished punished -> this.support.reply(actor,
                    new SakuraPunishResult.Success(punished.punishment(),
                            punished.superseded().orElse(null)));
            case LadderResult.ActionsOnly only -> this.runner.send(actor,
                    MessageKey.PUNISH_ACTIONS_ONLY, Placeholder.unparsed("name", ladder),
                    Placeholder.unparsed("victim", victim),
                    Placeholder.unparsed("step", Integer.toString(only.step() + 1)));
            case LadderResult.Failure failure -> this.support.reply(actor,
                    new SakuraPunishResult.Failure(failure.reason(),
                            failure.conflicting().orElse(null)));
        }
    }

    private SuggestionProvider<CommandActor> ladderNames() {
        return SuggestionProvider.blockingStrings((context, input) -> {
            final CommandActor actor = context.sender();
            final List<String> names = new ArrayList<>();
            for (final TemplateInfo template : this.templates.templates()) {
                if (template instanceof LadderTemplate && (!template.permissionRequired()
                        || actor.hasPermission(Permissions.template(template.name())))) {
                    names.add(template.name());
                }
            }
            return names;
        });
    }
}
