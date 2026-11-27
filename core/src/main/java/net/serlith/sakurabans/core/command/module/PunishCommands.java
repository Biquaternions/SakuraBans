package net.serlith.sakurabans.core.command.module;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import net.serlith.sakurabans.api.model.Actor;
import net.serlith.sakurabans.api.model.AddressVictim;
import net.serlith.sakurabans.api.model.CategoryScope;
import net.serlith.sakurabans.api.model.CompositeVictim;
import net.serlith.sakurabans.api.model.CustomOperator;
import net.serlith.sakurabans.api.model.EvidenceRef;
import net.serlith.sakurabans.api.model.GlobalScope;
import net.serlith.sakurabans.api.model.Operator;
import net.serlith.sakurabans.api.model.PlayerOperator;
import net.serlith.sakurabans.api.model.PlayerVictim;
import net.serlith.sakurabans.api.model.PunishmentType;
import net.serlith.sakurabans.api.model.Scope;
import net.serlith.sakurabans.api.model.ServerScope;
import net.serlith.sakurabans.api.model.Victim;
import net.serlith.sakurabans.api.model.Visibility;
import net.serlith.sakurabans.api.punish.DraftPunishment;
import net.serlith.sakurabans.api.punish.LadderRequest;
import net.serlith.sakurabans.api.punish.LadderResult;
import net.serlith.sakurabans.api.punish.OverridePolicy;
import net.serlith.sakurabans.api.punish.PunishOptions;
import net.serlith.sakurabans.api.punish.PunishResult;
import net.serlith.sakurabans.api.template.LadderTemplate;
import net.serlith.sakurabans.api.template.PresetTemplate;
import net.serlith.sakurabans.api.template.TemplateDuration;
import net.serlith.sakurabans.api.template.TemplateInfo;
import net.serlith.sakurabans.api.validation.ValidationException;
import net.serlith.sakurabans.api.validation.Validator;
import net.serlith.sakurabans.core.command.CommandActor;
import net.serlith.sakurabans.core.command.CommandModule;
import net.serlith.sakurabans.core.command.CommandRegistrar;
import net.serlith.sakurabans.core.command.CommandRunner;
import net.serlith.sakurabans.core.command.PunishFlags;
import net.serlith.sakurabans.core.command.Replies;
import net.serlith.sakurabans.core.command.ScopeAccess;
import net.serlith.sakurabans.core.command.parser.DurationParser;
import net.serlith.sakurabans.core.command.parser.TargetParser;
import net.serlith.sakurabans.core.configuration.ConfigService;
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
import net.serlith.sakurabans.core.util.Futures;
import org.incendo.cloud.Command;
import org.incendo.cloud.CommandManager;
import org.incendo.cloud.context.CommandContext;
import org.incendo.cloud.parser.flag.CommandFlag;
import org.incendo.cloud.parser.flag.FlagContext;
import org.incendo.cloud.parser.standard.StringParser;
import org.incendo.cloud.permission.Permission;
import org.incendo.cloud.suggestion.SuggestionProvider;
import org.jspecify.annotations.Nullable;

public final class PunishCommands implements CommandModule {

    private enum Mode { PLAIN, TEMPORARY, ADDRESS }

    private record Spec(String name, PunishmentType type, Mode mode, MessageKey description) {
    }

    private static final List<Spec> SPECS = List.of(
            new Spec("ban", PunishmentType.BAN, Mode.PLAIN, MessageKey.COMMAND_DESCRIPTION_BAN),
            new Spec("tempban", PunishmentType.BAN, Mode.TEMPORARY,
                    MessageKey.COMMAND_DESCRIPTION_TEMPBAN),
            new Spec("ipban", PunishmentType.BAN, Mode.ADDRESS,
                    MessageKey.COMMAND_DESCRIPTION_IPBAN),
            new Spec("mute", PunishmentType.MUTE, Mode.PLAIN, MessageKey.COMMAND_DESCRIPTION_MUTE),
            new Spec("tempmute", PunishmentType.MUTE, Mode.TEMPORARY,
                    MessageKey.COMMAND_DESCRIPTION_TEMPMUTE),
            new Spec("ipmute", PunishmentType.MUTE, Mode.ADDRESS,
                    MessageKey.COMMAND_DESCRIPTION_IPMUTE),
            new Spec("warn", PunishmentType.WARN, Mode.PLAIN, MessageKey.COMMAND_DESCRIPTION_WARN),
            new Spec("kick", PunishmentType.KICK, Mode.PLAIN,
                    MessageKey.COMMAND_DESCRIPTION_KICK));

    private final CommandRegistrar registrar;
    private final CommandRunner runner;
    private final PunishFlags flags;
    private final TargetParser targetParser;
    private final TargetResolver targets;
    private final SakuraPunishmentService punishments;
    private final ConfigService config;
    private final SakuraTemplateService templates;
    private final PunishSupport support;

    public PunishCommands(final CommandRegistrar registrar, final CommandRunner runner,
            final Replies replies, final PunishFlags flags, final TargetParser targetParser,
            final TargetResolver targets, final SakuraPunishmentService punishments,
            final NameCache names, final ConfigService config,
            final SakuraScopeService scopes, final ScopeAccess scopeAccess,
            final SakuraTemplateService templates) {
        this.registrar = registrar;
        this.runner = runner;
        this.flags = flags;
        this.targetParser = targetParser;
        this.targets = targets;
        this.punishments = punishments;
        this.config = config;
        this.templates = templates;
        this.support = new PunishSupport(registrar, runner, replies, names, scopes, scopeAccess);
    }

    @Override
    public void register(final CommandManager<CommandActor> manager) {
        for (final Spec spec : SPECS) {
            this.registrar.builder(manager, spec.name(), spec.description())
                    .map(builder -> this.build(builder, spec))
                    .ifPresent(manager::command);
        }
    }

    private Command.Builder<CommandActor> build(final Command.Builder<CommandActor> start,
            final Spec spec) {
        final PunishmentType type = spec.type();
        final Permission permission = spec.mode() == Mode.TEMPORARY
                ? Permission.anyOf(Permission.of(Permissions.punish(type)),
                        Permission.of(Permissions.temporary(type)))
                : Permission.of(Permissions.punish(type));
        Command.Builder<CommandActor> builder = start
                .permission(permission)
                .required("target", this.targetParser.descriptor());
        final List<CommandFlag<?>> flagList = this.flagsFor(spec);
        if (spec.mode() == Mode.TEMPORARY) {
            builder = builder.required("duration", DurationParser.descriptor());
        }
        for (final CommandFlag<?> flag : flagList) {
            builder = builder.flag(flag);
        }
        return builder.futureHandler(context -> this.runner.run(context.sender(),
                () -> this.execute(context, spec)));
    }

    private List<CommandFlag<?>> flagsFor(final Spec spec) {
        final PunishmentType type = spec.type();
        final List<CommandFlag<?>> flagList = new ArrayList<>(List.of(
                this.flags.silent(type),
                this.flags.hide(type),
                this.flags.global(),
                this.flags.server(),
                this.flags.category(),
                this.flags.scope(),
                this.flags.sender(),
                this.flags.senderUuid(),
                this.flags.skip(type),
                this.flags.template(this.templateNames(spec)),
                this.flags.evidence(type)));
        if (spec.mode() != Mode.ADDRESS) {
            flagList.add(this.flags.ip(type));
        }
        if (type == PunishmentType.MUTE) {
            flagList.add(this.flags.shadow(type));
        }
        if (!type.instant()) {
            flagList.add(this.flags.override(type));
            flagList.add(this.flags.ifLonger(type));
        }
        if (!type.instant() && spec.mode() != Mode.TEMPORARY) {
            flagList.add(this.flags.duration());
        }
        flagList.add(this.flags.reason());
        return List.copyOf(flagList);
    }

    private CompletableFuture<?> execute(final CommandContext<CommandActor> context,
            final Spec spec) {
        final CommandActor actor = context.sender();
        final FlagContext flags = context.flags();
        final PunishmentType type = spec.type();
        final Duration duration = spec.mode() == Mode.TEMPORARY
                ? context.<Duration>get("duration")
                : flags.<Duration>getValue(PunishFlags.DURATION).orElse(null);
        final String reason = PunishSupport.reason(flags);
        final TargetInput input = context.get("target");
        final Optional<Scope> scope = PunishSupport.scope(flags);
        final Actor issuer = PunishSupport.issuer(actor, flags);
        if (issuer == null) {
            this.runner.send(actor, MessageKey.COMMAND_SENDER_NEEDS_NAME);
            return CompletableFuture.completedFuture(null);
        }
        final Shortcut shortcut = this.shortcut(actor, spec, reason,
                flags.<String>getValue(PunishFlags.TEMPLATE).orElse(null), duration != null,
                input, scope.orElse(null), issuer);
        if (shortcut == null) {
            return CompletableFuture.completedFuture(null);
        }
        if (!this.support.scopeAllowed(actor, scope.orElse(null), shortcut.template())) {
            return CompletableFuture.completedFuture(null);
        }
        final List<EvidenceRef> evidence = this.support.evidence(actor, flags);
        if (evidence == null) {
            return CompletableFuture.completedFuture(null);
        }
        final boolean address = spec.mode() == Mode.ADDRESS || flags.isPresent(PunishFlags.IP)
                || this.config.settings().enforcement().compositeByDefault()
                        && type != PunishmentType.KICK;
        return this.targets.resolve(input).thenCompose(target -> {
            final Victim victim = this.support.victim(actor,
                    Permissions.flag(type, PunishFlags.IP), Permissions.range(type), target,
                    address);
            if (victim == null) {
                return CompletableFuture.completedFuture(null);
            }
            final DraftPunishment draft;
            try {
                draft = DraftPunishment.builder(type, victim, issuer)
                        .scope(scope.orElse(null))
                        .reason(shortcut.reason())
                        .duration(duration)
                        .template(shortcut.template() == null ? null
                                : shortcut.template().name())
                        .shadow(flags.isPresent(PunishFlags.SHADOW))
                        .evidence(evidence)
                        .build();
            } catch (final ValidationException e) {
                this.support.invalid(actor, e);
                return CompletableFuture.completedFuture(null);
            }
            return this.punishments.punish(draft, PunishSupport.options(flags))
                    .thenAccept(result -> this.support.reply(actor, result))
                    .exceptionally(failure -> this.support.invalidOrRethrow(actor, failure));
        });
    }

    private record Shortcut(@Nullable TemplateInfo template, @Nullable String reason) {
    }

    private @Nullable Shortcut shortcut(final CommandActor actor, final Spec spec,
            final @Nullable String reason, final @Nullable String given,
            final boolean withDuration, final TargetInput input, final @Nullable Scope scope,
            final Actor issuer) {
        if (given == null) {
            return new Shortcut(null, reason);
        }
        final String name = given.strip().toLowerCase(Locale.ROOT);
        // The variant the service will use (S62): the same scope and issuer.
        final TemplateInfo template = this.templates.resolve(name, scope, issuer)
                .map(StoredTemplate::template).orElse(null);
        if (template != null) {
            final TagResolver[] names = {Placeholder.unparsed("name", template.name()),
                    Placeholder.unparsed("player", inputText(input))};
            if (!(template instanceof PresetTemplate preset)) {
                this.runner.send(actor, MessageKey.PUNISH_FAILURE_TEMPLATE_LADDER,
                        TagResolver.resolver(names),
                        this.support.command(LadderCommands.PUNISH));
                return null;
            }
            if (preset.type() != spec.type()) {
                this.runner.send(actor, MessageKey.PUNISH_FAILURE_TEMPLATE_TYPE,
                        TagResolver.resolver(names),
                        this.support.command(PunishSupport.plain(preset.type())),
                        Placeholder.component("type", this.runner.render(actor,
                                Placeholders.typeKey(preset.type()))));
                return null;
            }
            if (withDuration && !(preset.duration() instanceof TemplateDuration.Open)) {
                this.runner.send(actor, MessageKey.PUNISH_FAILURE_TEMPLATE_DURATION,
                        TagResolver.resolver(names),
                        this.support.command(PunishSupport.plain(preset.type())));
                return null;
            }
            if (template.permissionRequired()
                    && !actor.hasPermission(Permissions.template(template.name()))) {
                this.runner.send(actor, MessageKey.PUNISH_FAILURE_TEMPLATE_NO_PERMISSION,
                        Placeholder.unparsed("name", template.name()));
                return null;
            }
            return new Shortcut(template, reason);
        }
        final String text = this.config.settings().reasons().shortcuts().get(name);
        if (text == null) {
            this.runner.send(actor, MessageKey.PUNISH_FAILURE_NO_TEMPLATE,
                    Placeholder.unparsed("name", name));
            return null;
        }
        return new Shortcut(null, reason == null ? text : text + " " + reason);
    }

    /** Presets of the command's type; a temp command only those that leave the duration open. */
    private SuggestionProvider<CommandActor> templateNames(final Spec spec) {
        return SuggestionProvider.blockingStrings((context, input) -> {
            final CommandActor actor = context.sender();
            final List<String> names = new ArrayList<>();
            for (final TemplateInfo template : this.templates.templates()) {
                if (template instanceof PresetTemplate preset && preset.type() == spec.type()
                        && (spec.mode() != Mode.TEMPORARY
                                || preset.duration() instanceof TemplateDuration.Open)
                        && (!template.permissionRequired()
                        || actor.hasPermission(Permissions.template(template.name())))) {
                    names.add(template.name());
                }
            }
            this.config.settings().reasons().shortcuts().keySet().stream().sorted()
                    .filter(shortcut -> !names.contains(shortcut))
                    .forEach(names::add);
            return names;
        });
    }

    static String displayName(final ResolvedTarget.Player player) {
        return player.name() == null ? player.uuid().toString() : player.name();
    }

    static String inputText(final TargetInput input) {
        return switch (input) {
            case TargetInput.NameInput name -> name.name();
            case TargetInput.UuidInput uuid -> uuid.uuid().toString();
            case TargetInput.AddressInput address -> address.address().getHostAddress();
            case TargetInput.RangeInput range -> range.range().network().getHostAddress() + "/"
                    + range.range().prefixLength();
            case TargetInput.IdInput id -> "#" + id.id();
        };
    }

}
