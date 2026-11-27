package net.serlith.sakurabans.core.command.module;

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
import net.serlith.sakurabans.api.punish.OverridePolicy;
import net.serlith.sakurabans.api.punish.PunishOptions;
import net.serlith.sakurabans.api.punish.PunishResult;
import net.serlith.sakurabans.api.template.TemplateInfo;
import net.serlith.sakurabans.api.validation.ValidationException;
import net.serlith.sakurabans.api.validation.Validator;
import net.serlith.sakurabans.core.command.CommandActor;
import net.serlith.sakurabans.core.command.CommandRegistrar;
import net.serlith.sakurabans.core.command.CommandRunner;
import net.serlith.sakurabans.core.command.PunishFlags;
import net.serlith.sakurabans.core.command.Replies;
import net.serlith.sakurabans.core.command.ScopeAccess;
import net.serlith.sakurabans.core.message.MessageKey;
import net.serlith.sakurabans.core.model.ResolvedTarget;
import net.serlith.sakurabans.core.player.NameCache;
import net.serlith.sakurabans.core.scope.SakuraScopeService;
import net.serlith.sakurabans.core.util.Futures;
import org.incendo.cloud.parser.flag.FlagContext;
import org.jspecify.annotations.Nullable;

/**
 * What the punish commands ({@link PunishCommands}) and {@code /punish} ({@link LadderCommands})
 * share: reading their flags, the victim, the scope check and the replies. Stateless; each
 * module makes one from the collaborators it is given.
 */
final class PunishSupport {

    private final CommandRegistrar registrar;
    private final CommandRunner runner;
    private final Replies replies;
    private final NameCache names;
    private final SakuraScopeService scopes;
    private final ScopeAccess scopeAccess;

    PunishSupport(final CommandRegistrar registrar, final CommandRunner runner,
            final Replies replies, final NameCache names, final SakuraScopeService scopes,
            final ScopeAccess scopeAccess) {
        this.registrar = registrar;
        this.runner = runner;
        this.replies = replies;
        this.names = names;
        this.scopes = scopes;
        this.scopeAccess = scopeAccess;
    }

    /** The command of a type that takes its presets (and an open duration). */
    static String plain(final PunishmentType type) {
        return type.name().toLowerCase(Locale.ROOT);
    }

    TagResolver command(final String command) {
        return Placeholder.unparsed("command", this.registrar.label(command).orElse(command));
    }

    /** The scope that results (the command's, else the template's, else the default). */
    boolean scopeAllowed(final CommandActor actor, final @Nullable Scope given,
            final @Nullable TemplateInfo template) {
        final Optional<Scope> checked = Optional.ofNullable(given).or(() -> Optional
                .ofNullable(template).flatMap(TemplateInfo::scope));
        if (checked.isPresent() && !this.scopeAccess.mayUse(actor, checked.get())) {
            this.runner.send(actor, MessageKey.COMMAND_SCOPE_NO_PERMISSION,
                    Placeholder.unparsed("scope", this.scopes.format(checked.get())));
            return false;
        }
        if (checked.isEmpty() && !this.scopeAccess.mayUseDefault(actor)) {
            this.runner.send(actor, MessageKey.COMMAND_SCOPE_DEFAULT_NO_PERMISSION);
            return false;
        }
        return true;
    }

    @Nullable List<EvidenceRef> evidence(final CommandActor actor, final FlagContext flags) {
        final List<EvidenceRef> evidence = new ArrayList<>();
        for (final String text : flags.<String>getAll(PunishFlags.EVIDENCE)) {
            try {
                evidence.add(EvidenceRef.of(text));
            } catch (final ValidationException e) {
                this.runner.send(actor, MessageKey.COMMAND_INVALID,
                        Placeholder.unparsed("details", e.getMessage()));
                return null;
            }
        }
        return evidence;
    }

    static @Nullable String reason(final FlagContext flags) {
        return flags.<String>getValue(PunishFlags.REASON).map(String::strip)
                .filter(text -> !text.isEmpty()).orElse(null);
    }

    @Nullable Victim victim(final CommandActor actor, final String ipPermission,
            final String rangePermission, final ResolvedTarget target, final boolean address) {
        return switch (target) {
            case ResolvedTarget.Player player -> {
                if (player.name() != null) {
                    this.names.put(player.uuid(), player.name());
                }
                if (!address) {
                    yield PlayerVictim.of(player.uuid());
                }
                if (!actor.hasPermission(ipPermission)) {
                    this.runner.send(actor, MessageKey.COMMAND_ADDRESS_NO_PERMISSION);
                    yield null;
                }
                if (player.lastAddress() == null) {
                    this.runner.send(actor, MessageKey.COMMAND_NO_ADDRESS,
                            Placeholder.unparsed("name", PunishCommands.displayName(player)));
                    yield null;
                }
                yield CompositeVictim.of(player.uuid(), player.lastAddress());
            }
            case ResolvedTarget.Address found -> {
                if (!actor.hasPermission(ipPermission)) {
                    this.runner.send(actor, MessageKey.COMMAND_ADDRESS_NO_PERMISSION);
                    yield null;
                }
                yield AddressVictim.of(found.address());
            }
            case ResolvedTarget.Range range -> {
                if (!actor.hasPermission(rangePermission)) {
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
                        Placeholder.unparsed("input", PunishCommands.inputText(unknown.input())));
                yield null;
            }
        };
    }

    static Optional<Scope> scope(final FlagContext flags) {
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

    static @Nullable Actor issuer(final CommandActor actor, final FlagContext flags) {
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

    static PunishOptions options(final FlagContext flags) {
        return PunishOptions.builder()
                .visibility(flags.isPresent(PunishFlags.HIDE) ? Visibility.HIDDEN
                        : flags.isPresent(PunishFlags.SILENT) ? Visibility.SILENT
                        : Visibility.NORMAL)
                .override(flags.isPresent(PunishFlags.OVERRIDE) ? OverridePolicy.REPLACE
                        : flags.isPresent(PunishFlags.IF_LONGER)
                                ? OverridePolicy.REPLACE_IF_LONGER
                                : OverridePolicy.REJECT)
                .enforce(!flags.isPresent(PunishFlags.SKIP))
                .build();
    }

    void reply(final CommandActor actor, final PunishResult result) {
        switch (result) {
            // The punishment's own type: a template's step may make a /ban a kick.
            case PunishResult.Success success -> this.runner.send(actor, switch (
                    success.punishment().type()) {
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

    /** Replies to a validation failure of the service; any other failure goes on. */
    @Nullable Void invalidOrRethrow(final CommandActor actor, final Throwable failure) {
        if (Futures.unwrap(failure) instanceof ValidationException e) {
            this.invalid(actor, e);
            return null;
        }
        throw new CompletionException(failure);
    }

    void invalid(final CommandActor actor, final ValidationException e) {
        this.runner.send(actor, MessageKey.COMMAND_INVALID, Placeholder.unparsed("details",
                String.join("; ", e.errors().stream()
                        .map(error -> error.path() + " " + error.message()).toList())));
    }

    private static MessageKey failureKey(final PunishResult.Failure.Reason reason) {
        return switch (reason) {
            // Only reached without a conflicting punishment, so the message has no placeholders.
            case ALREADY_PUNISHED -> MessageKey.PUNISH_FAILURE_ALREADY_PUNISHED_GENERIC;
            case EXEMPT -> MessageKey.PUNISH_FAILURE_EXEMPT;
            case CANCELLED -> MessageKey.PUNISH_FAILURE_CANCELLED;
            case LIMIT_EXCEEDED -> MessageKey.PUNISH_FAILURE_LIMIT_EXCEEDED;
            case COOLDOWN -> MessageKey.PUNISH_FAILURE_COOLDOWN;
            case REASON_REQUIRED -> MessageKey.PUNISH_FAILURE_REASON_REQUIRED;
            case TEMPLATE_REQUIRED -> MessageKey.PUNISH_FAILURE_TEMPLATE_REQUIRED;
            case UNKNOWN_TEMPLATE -> MessageKey.PUNISH_FAILURE_UNKNOWN_TEMPLATE;
            case TEMPLATE_TYPE_MISMATCH -> MessageKey.PUNISH_FAILURE_TEMPLATE_TYPE_MISMATCH;
            case FORBIDDEN -> MessageKey.PUNISH_FAILURE_FORBIDDEN;
        };
    }
}
