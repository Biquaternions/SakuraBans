package net.serlith.sakurabans.core.command.module;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.serlith.sakurabans.api.model.Actor;
import net.serlith.sakurabans.api.model.AddressVictim;
import net.serlith.sakurabans.api.model.CategoryScope;
import net.serlith.sakurabans.api.model.CompositeVictim;
import net.serlith.sakurabans.api.model.CustomOperator;
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
import net.serlith.sakurabans.api.punish.Enforcement;
import net.serlith.sakurabans.api.punish.OverridePolicy;
import net.serlith.sakurabans.api.punish.PunishOptions;
import net.serlith.sakurabans.api.punish.PunishResult;
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
import net.serlith.sakurabans.core.command.parser.PunishTail;
import net.serlith.sakurabans.core.command.parser.TargetParser;
import net.serlith.sakurabans.core.configuration.ConfigService;
import net.serlith.sakurabans.core.message.MessageKey;
import net.serlith.sakurabans.core.model.ResolvedTarget;
import net.serlith.sakurabans.core.model.TargetInput;
import net.serlith.sakurabans.core.permission.Permissions;
import net.serlith.sakurabans.core.player.NameCache;
import net.serlith.sakurabans.core.player.TargetResolver;
import net.serlith.sakurabans.core.punish.SakuraPunishmentService;
import net.serlith.sakurabans.core.scope.SakuraScopeService;
import net.serlith.sakurabans.core.util.Futures;
import org.incendo.cloud.Command;
import org.incendo.cloud.CommandManager;
import org.incendo.cloud.context.CommandContext;
import org.incendo.cloud.parser.flag.FlagContext;
import org.incendo.cloud.parser.standard.StringParser;
import org.incendo.cloud.permission.Permission;
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
    private final Replies replies;
    private final PunishFlags flags;
    private final TargetParser targetParser;
    private final TargetResolver targets;
    private final SakuraPunishmentService punishments;
    private final NameCache names;
    private final ConfigService config;
    private final SakuraScopeService scopes;
    private final ScopeAccess scopeAccess;

    public PunishCommands(final CommandRegistrar registrar, final CommandRunner runner,
            final Replies replies, final PunishFlags flags, final TargetParser targetParser,
            final TargetResolver targets, final SakuraPunishmentService punishments,
            final NameCache names, final ConfigService config,
            final SakuraScopeService scopes, final ScopeAccess scopeAccess) {
        this.registrar = registrar;
        this.runner = runner;
        this.replies = replies;
        this.flags = flags;
        this.targetParser = targetParser;
        this.targets = targets;
        this.punishments = punishments;
        this.names = names;
        this.config = config;
        this.scopes = scopes;
        this.scopeAccess = scopeAccess;
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
        Command.Builder<CommandActor> builder = start
                .permission(spec.mode() == Mode.TEMPORARY
                        ? Permission.anyOf(Permission.of(Permissions.punish(type)),
                                Permission.of(Permissions.temporary(type)))
                        : Permission.of(Permissions.punish(type)))
                .required("target", this.targetParser.descriptor());
        if (spec.mode() == Mode.TEMPORARY) {
            builder = builder.required("duration", DurationParser.descriptor());
        }
        builder = builder.optional("tail", StringParser.greedyFlagYieldingStringParser())
                .flag(this.flags.silent(type))
                .flag(this.flags.publicFlag(type))
                .flag(this.flags.hide(type))
                .flag(this.flags.global())
                .flag(this.flags.server())
                .flag(this.flags.category())
                .flag(this.flags.scope())
                .flag(this.flags.sender())
                .flag(this.flags.senderUuid())
                .flag(this.flags.skip(type));
        if (spec.mode() != Mode.ADDRESS) {
            builder = builder.flag(this.flags.ip(type));
        }
        if (!type.instant()) {
            builder = builder.flag(this.flags.override(type)).flag(this.flags.ifLonger(type));
        }
        return builder.futureHandler(context -> this.runner.run(context.sender(),
                () -> this.execute(context, spec)));
    }

    private CompletableFuture<?> execute(final CommandContext<CommandActor> context,
            final Spec spec) {
        final CommandActor actor = context.sender();
        final FlagContext flags = context.flags();
        final PunishmentType type = spec.type();
        final PunishTail tail = PunishTail.parse(context.<String>optional("tail").orElse(null),
                spec.mode() != Mode.TEMPORARY && !type.instant());
        final Duration duration = spec.mode() == Mode.TEMPORARY
                ? context.<Duration>get("duration")
                : tail.duration();
        final Optional<Scope> scope = this.scope(flags);
        if (scope.isPresent() && !this.scopeAccess.mayUse(actor, scope.get())) {
            this.runner.send(actor, MessageKey.COMMAND_SCOPE_NO_PERMISSION,
                    Placeholder.unparsed("scope", this.scopes.format(scope.get())));
            return CompletableFuture.completedFuture(null);
        }
        if (scope.isEmpty() && !this.scopeAccess.mayUseDefault(actor)) {
            this.runner.send(actor, MessageKey.COMMAND_SCOPE_DEFAULT_NO_PERMISSION);
            return CompletableFuture.completedFuture(null);
        }
        final Actor issuer = this.issuer(actor, flags);
        if (issuer == null) {
            this.runner.send(actor, MessageKey.COMMAND_SENDER_NEEDS_NAME);
            return CompletableFuture.completedFuture(null);
        }
        final boolean address = spec.mode() == Mode.ADDRESS || flags.isPresent(PunishFlags.IP)
                || this.config.settings().enforcement().compositeByDefault()
                        && type != PunishmentType.KICK;
        final TargetInput input = context.get("target");
        return this.targets.resolve(input).thenCompose(target -> {
            final Victim victim = this.victim(actor, type, target, address);
            if (victim == null) {
                return CompletableFuture.completedFuture(null);
            }
            final DraftPunishment draft;
            try {
                draft = DraftPunishment.builder(type, victim, issuer)
                        .scope(scope.orElse(null))
                        .reason(tail.reason())
                        .duration(duration)
                        .template(tail.template())
                        .build();
            } catch (final ValidationException e) {
                this.invalid(actor, e);
                return CompletableFuture.completedFuture(null);
            }
            return this.punishments.punish(draft, this.options(flags))
                    .thenAccept(result -> this.reply(actor, type, result))
                    .exceptionally(failure -> {
                        if (Futures.unwrap(failure) instanceof ValidationException e) {
                            this.invalid(actor, e);
                            return null;
                        }
                        throw new CompletionException(failure);
                    });
        });
    }

    private @Nullable Victim victim(final CommandActor actor, final PunishmentType type,
            final ResolvedTarget target, final boolean address) {
        return switch (target) {
            case ResolvedTarget.Player player -> {
                if (player.name() != null) {
                    this.names.put(player.uuid(), player.name());
                }
                if (!address) {
                    yield PlayerVictim.of(player.uuid());
                }
                if (!actor.hasPermission(Permissions.flag(type, PunishFlags.IP))) {
                    this.runner.send(actor, MessageKey.COMMAND_ADDRESS_NO_PERMISSION);
                    yield null;
                }
                if (player.lastAddress() == null) {
                    this.runner.send(actor, MessageKey.COMMAND_NO_ADDRESS,
                            Placeholder.unparsed("name", displayName(player)));
                    yield null;
                }
                yield CompositeVictim.of(player.uuid(), player.lastAddress());
            }
            case ResolvedTarget.Address found -> {
                if (!actor.hasPermission(Permissions.flag(type, PunishFlags.IP))) {
                    this.runner.send(actor, MessageKey.COMMAND_ADDRESS_NO_PERMISSION);
                    yield null;
                }
                yield AddressVictim.of(found.address());
            }
            case ResolvedTarget.Range range -> {
                if (!actor.hasPermission(Permissions.range(type))) {
                    this.runner.send(actor, MessageKey.COMMAND_RANGE_NO_PERMISSION);
                    yield null;
                }
                yield range.range();
            }
            case ResolvedTarget.PunishmentId id -> {
                this.runner.send(actor, MessageKey.COMMAND_ID_NOT_ALLOWED);
                yield null;
            }
            case ResolvedTarget.Unknown unknown -> {
                this.runner.send(actor, MessageKey.COMMAND_UNKNOWN_TARGET,
                        Placeholder.unparsed("input", inputText(unknown.input())));
                yield null;
            }
        };
    }

    private Optional<Scope> scope(final FlagContext flags) {
        if (flags.isPresent(PunishFlags.GLOBAL)) {
            return Optional.of(GlobalScope.INSTANCE);
        }
        final Optional<Scope> server = flags.<String>getValue(PunishFlags.SERVER)
                .flatMap(Validator::normalizeIdentifier)
                .map(ServerScope::of);
        if (server.isPresent()) {
            return server;
        }
        final Optional<Scope> category = flags.<String>getValue(PunishFlags.CATEGORY)
                .flatMap(Validator::normalizeIdentifier)
                .map(CategoryScope::of);
        if (category.isPresent()) {
            return category;
        }
        return flags.getValue(PunishFlags.SCOPE);
    }

    private @Nullable Actor issuer(final CommandActor actor, final FlagContext flags) {
        final Optional<String> name = flags.getValue(PunishFlags.SENDER);
        final Optional<UUID> uuid = flags.getValue(PunishFlags.SENDER_UUID);
        if (name.isEmpty()) {
            return uuid.isPresent() ? null : actor.asActor();
        }
        final Operator operator = uuid
                .<Operator>map(id -> PlayerOperator.of(id, name.get()))
                .orElseGet(() -> CustomOperator.of(name.get(), null));
        return Actor.of(operator, CommandActor.SOURCE_CONSOLE);
    }

    private PunishOptions options(final FlagContext flags) {
        return PunishOptions.builder()
                .visibility(flags.isPresent(PunishFlags.HIDE) ? Visibility.HIDDEN
                        : flags.isPresent(PunishFlags.SILENT) ? Visibility.SILENT
                        : Visibility.NORMAL)
                .override(flags.isPresent(PunishFlags.OVERRIDE) ? OverridePolicy.REPLACE
                        : flags.isPresent(PunishFlags.IF_LONGER)
                                ? OverridePolicy.REPLACE_IF_LONGER
                                : OverridePolicy.REJECT)
                .enforcement(flags.isPresent(PunishFlags.SKIP) ? Enforcement.NONE
                        : Enforcement.GLOBAL)
                .build();
    }

    private void reply(final CommandActor actor, final PunishmentType type,
            final PunishResult result) {
        switch (result) {
            case PunishResult.Success success -> this.runner.send(actor, switch (type) {
                case BAN -> MessageKey.PUNISH_BAN;
                case MUTE -> MessageKey.PUNISH_MUTE;
                case WARN -> MessageKey.PUNISH_WARN;
                case KICK -> MessageKey.PUNISH_KICK;
            }, this.replies.punishment(actor, success.punishment()));
            case PunishResult.Failure failure -> {
                if (failure.conflicting().isPresent()) {
                    this.runner.send(actor, MessageKey.PUNISH_FAILURE_ALREADY_PUNISHED,
                            this.replies.punishment(actor, failure.conflicting().get()));
                } else {
                    this.runner.send(actor, failureKey(failure.reason()));
                }
            }
        }
    }

    private void invalid(final CommandActor actor, final ValidationException e) {
        this.runner.send(actor, MessageKey.COMMAND_INVALID, Placeholder.unparsed("details",
                String.join("; ", e.errors().stream()
                        .map(error -> error.path() + " " + error.message()).toList())));
    }

    private static MessageKey failureKey(final PunishResult.Failure.Reason reason) {
        return switch (reason) {
            case ALREADY_PUNISHED -> MessageKey.PUNISH_FAILURE_ALREADY_PUNISHED;
            case EXEMPT -> MessageKey.PUNISH_FAILURE_EXEMPT;
            case CANCELLED -> MessageKey.PUNISH_FAILURE_CANCELLED;
            case LIMIT_EXCEEDED -> MessageKey.PUNISH_FAILURE_LIMIT_EXCEEDED;
            case COOLDOWN -> MessageKey.PUNISH_FAILURE_COOLDOWN;
            case REASON_REQUIRED -> MessageKey.PUNISH_FAILURE_REASON_REQUIRED;
            case TEMPLATE_REQUIRED -> MessageKey.PUNISH_FAILURE_TEMPLATE_REQUIRED;
            case UNKNOWN_TEMPLATE -> MessageKey.PUNISH_FAILURE_UNKNOWN_TEMPLATE;
            case UNKNOWN_KIND -> MessageKey.PUNISH_FAILURE_UNKNOWN_KIND;
            case EVIDENCE_REQUIRED -> MessageKey.PUNISH_FAILURE_EVIDENCE_REQUIRED;
            case FORBIDDEN -> MessageKey.PUNISH_FAILURE_FORBIDDEN;
        };
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
