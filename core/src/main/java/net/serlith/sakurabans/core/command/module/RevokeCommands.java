package net.serlith.sakurabans.core.command.module;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.serlith.sakurabans.api.model.AddressStrictness;
import net.serlith.sakurabans.api.model.AddressVictim;
import net.serlith.sakurabans.api.model.Punishment;
import net.serlith.sakurabans.api.model.PunishmentType;
import net.serlith.sakurabans.api.model.Scope;
import net.serlith.sakurabans.api.model.Visibility;
import net.serlith.sakurabans.api.punish.PunishmentQuery;
import net.serlith.sakurabans.api.punish.RevocationRequest;
import net.serlith.sakurabans.api.punish.RevocationTarget;
import net.serlith.sakurabans.api.punish.RevokeResult;
import net.serlith.sakurabans.api.validation.ValidationException;
import net.serlith.sakurabans.core.command.CommandActor;
import net.serlith.sakurabans.core.command.CommandModule;
import net.serlith.sakurabans.core.command.CommandRegistrar;
import net.serlith.sakurabans.core.command.CommandRunner;
import net.serlith.sakurabans.core.command.PunishFlags;
import net.serlith.sakurabans.core.command.Replies;
import net.serlith.sakurabans.core.command.ScopeAccess;
import net.serlith.sakurabans.core.command.parser.ScopeFilter;
import net.serlith.sakurabans.core.command.parser.TargetParser;
import net.serlith.sakurabans.core.message.MessageKey;
import net.serlith.sakurabans.core.model.ResolvedTarget;
import net.serlith.sakurabans.core.permission.Permissions;
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

public final class RevokeCommands implements CommandModule {

    private record Spec(String name, PunishmentType type, MessageKey description,
            MessageKey success) {
    }

    private static final List<Spec> SPECS = List.of(
            new Spec("unban", PunishmentType.BAN, MessageKey.COMMAND_DESCRIPTION_UNBAN,
                    MessageKey.REVOKE_BAN),
            new Spec("pardon", PunishmentType.BAN, MessageKey.COMMAND_DESCRIPTION_PARDON,
                    MessageKey.REVOKE_BAN),
            new Spec("unmute", PunishmentType.MUTE, MessageKey.COMMAND_DESCRIPTION_UNMUTE,
                    MessageKey.REVOKE_MUTE),
            new Spec("unwarn", PunishmentType.WARN, MessageKey.COMMAND_DESCRIPTION_UNWARN,
                    MessageKey.REVOKE_WARN));

    private final CommandRegistrar registrar;
    private final CommandRunner runner;
    private final Replies replies;
    private final PunishFlags flags;
    private final TargetParser targetParser;
    private final TargetResolver targets;
    private final SakuraPunishmentService punishments;
    private final SakuraScopeService scopes;
    private final ScopeAccess scopeAccess;

    public RevokeCommands(final CommandRegistrar registrar, final CommandRunner runner,
            final Replies replies, final PunishFlags flags, final TargetParser targetParser,
            final TargetResolver targets, final SakuraPunishmentService punishments,
            final SakuraScopeService scopes, final ScopeAccess scopeAccess) {
        this.registrar = registrar;
        this.runner = runner;
        this.replies = replies;
        this.flags = flags;
        this.targetParser = targetParser;
        this.targets = targets;
        this.punishments = punishments;
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
        return start
                .permission(Permission.anyOf(Permission.of(Permissions.revoke(type)),
                        Permission.of(Permissions.revokeOwn(type))))
                .required("target", this.targetParser.descriptor())
                .optional("reason", StringParser.greedyFlagYieldingStringParser())
                .flag(this.flags.silent(type))
                .flag(this.flags.hide(type))
                .flag(this.flags.scopeFilter())
                .futureHandler(context -> this.runner.run(context.sender(),
                        () -> this.execute(context, spec)));
    }

    private CompletableFuture<?> execute(final CommandContext<CommandActor> context,
            final Spec spec) {
        final CommandActor actor = context.sender();
        final FlagContext flags = context.flags();
        // No --scope and --scope all both mean every scope.
        final Scope scope = flags.<ScopeFilter>getValue(PunishFlags.SCOPE)
                .flatMap(ScopeFilter::scope).orElse(null);
        final Visibility visibility = flags.isPresent(PunishFlags.HIDE) ? Visibility.HIDDEN
                : flags.isPresent(PunishFlags.SILENT) ? Visibility.SILENT : Visibility.NORMAL;
        final String reason = context.<String>optional("reason").map(String::strip)
                .filter(text -> !text.isEmpty()).orElse(null);
        final boolean ownOnly = !actor.hasPermission(Permissions.revoke(spec.type()));
        final RevocationRequest.Builder template;
        try {
            // Validates the reason now; the target is replaced per punishment below.
            template = RevocationRequest.builder(RevocationTarget.ById.of(1), actor.asActor())
                    .reason(reason).visibility(visibility).ownOnly(ownOnly);
            template.build();
        } catch (final ValidationException e) {
            this.runner.send(actor, MessageKey.COMMAND_INVALID,
                    Placeholder.unparsed("details", e.getMessage()));
            return CompletableFuture.completedFuture(null);
        }
        if (scope != null && !this.scopeAccess.mayUse(actor, scope)) {
            this.runner.send(actor, MessageKey.REVOKE_FAILURE_SCOPE_NO_PERMISSION,
                    Placeholder.unparsed("scopes", this.scopes.format(scope)));
            return CompletableFuture.completedFuture(null);
        }
        return this.targets.resolve(context.get("target"))
                .thenCompose(target -> this.revocationTargets(actor, spec.type(), target, scope))
                .thenCompose(found -> found
                        .flatMap(value -> this.permitted(actor, value))
                        .map(value -> this.revokeAll(actor, spec, value, reason, visibility,
                                ownOnly))
                        .orElseGet(() -> CompletableFuture.completedFuture(null))
                );
    }

    private record Found(List<Punishment> punishments, String name, List<Scope> skipped) {
    }

    /** Empty after telling the sender why. */
    private CompletableFuture<Optional<Found>> revocationTargets(
            final CommandActor actor, final PunishmentType type, final ResolvedTarget target,
            final @Nullable Scope scope) {
        return switch (target) {
            case ResolvedTarget.PunishmentId id -> this.punishments.byId(id.id())
                    .thenApply(found -> {
                        if (found.isEmpty()) {
                            this.runner.send(actor, MessageKey.REVOKE_FAILURE_NOT_FOUND);
                            return Optional.empty();
                        }
                        if (found.get().type() != type) {
                            this.runner.send(actor, MessageKey.REVOKE_FAILURE_WRONG_TYPE,
                                    this.replies.punishment(actor, found.get()));
                            return Optional.empty();
                        }
                        return Optional.of(new Found(List.of(found.get()),
                                "#" + found.get().displayId(), List.of()));
                    });
            case ResolvedTarget.Player player -> this.active(actor, PunishmentQuery.builder()
                    .applicableTo(PunishmentQuery.Applicability.of(player.uuid(), null,
                            AddressStrictness.LENIENT)), type, scope,
                    PunishCommands.displayName(player));
            case ResolvedTarget.Address address -> this.active(actor, PunishmentQuery.builder()
                    .victim(AddressVictim.of(address.address())), type, scope,
                    this.replies.victim(actor, AddressVictim.of(address.address())));
            case ResolvedTarget.Range range -> this.active(actor, PunishmentQuery.builder()
                    .victim(range.range()), type, scope,
                    this.replies.victim(actor, range.range()));
            case ResolvedTarget.Unknown unknown -> {
                this.runner.send(actor, MessageKey.COMMAND_UNKNOWN_TARGET,
                        Placeholder.unparsed("input", PunishCommands.inputText(unknown.input())));
                yield CompletableFuture.completedFuture(Optional.empty());
            }
        };
    }

    private CompletableFuture<Optional<Found>> active(final CommandActor actor,
            final PunishmentQuery.Builder query, final PunishmentType type,
            final @Nullable Scope scope, final String name) {
        return this.punishments.query(query
                        .types(type)
                        .scope(scope)
                        .activeOnly(true)
                        .order(PunishmentQuery.Order.NEWEST_FIRST)
                        // A warning is removed one at a time, the newest first.
                        .pageSize(type.stacks() ? 1 : PunishmentQuery.MAX_PAGE_SIZE)
                        .build())
                .thenApply(page -> {
                    if (page.items().isEmpty()) {
                        this.runner.send(actor, MessageKey.REVOKE_FAILURE_NOT_FOUND);
                        return Optional.empty();
                    }
                    return Optional.of(new Found(page.items(), name, List.of()));
                });
    }

    private Optional<Found> permitted(final CommandActor actor, final Found found) {
        final List<Punishment> allowed = new ArrayList<>();
        final Set<Scope> skipped = new LinkedHashSet<>();
        for (final Punishment punishment : found.punishments()) {
            if (this.scopeAccess.mayUse(actor, punishment.scope())) {
                allowed.add(punishment);
            } else {
                skipped.add(punishment.scope());
            }
        }
        if (allowed.isEmpty()) {
            this.runner.send(actor, MessageKey.REVOKE_FAILURE_SCOPE_NO_PERMISSION,
                    Placeholder.unparsed("scopes", this.formatAll(skipped)));
            return Optional.empty();
        }
        return Optional.of(new Found(List.copyOf(allowed), found.name(), List.copyOf(skipped)));
    }

    private String formatAll(final Collection<Scope> scopes) {
        return scopes.stream().map(this.scopes::format).collect(Collectors.joining(", "));
    }

    private CompletableFuture<Void> revokeAll(final CommandActor actor, final Spec spec,
            final Found found, final @Nullable String reason,
            final Visibility visibility, final boolean ownOnly) {
        final List<CompletableFuture<RevokeResult>> results = found.punishments().stream()
                .map(punishment -> this.punishments.revoke(RevocationRequest.builder(
                                RevocationTarget.ById.of(punishment.id()), actor.asActor())
                        .reason(reason).visibility(visibility).ownOnly(ownOnly).build()))
                .toList();
        return Futures.allOf(results).thenAccept(all -> {
            final List<Punishment> revoked = new ArrayList<>();
            RevokeResult.Failure firstFailure = null;
            for (final RevokeResult result : all) {
                switch (result) {
                    case RevokeResult.Success success -> revoked.addAll(success.revoked());
                    case RevokeResult.Failure failure -> {
                        if (firstFailure == null) {
                            firstFailure = failure;
                        }
                    }
                }
            }
            if (revoked.isEmpty()) {
                this.runner.send(actor, failureKey(firstFailure == null
                        ? RevokeResult.Failure.Reason.NOT_FOUND
                        : firstFailure.reason()));
                return;
            }
            this.runner.send(actor, spec.success(),
                    Placeholder.unparsed("target", found.name()),
                    Placeholder.unparsed("count", Integer.toString(revoked.size())));
            if (!found.skipped().isEmpty()) {
                this.runner.send(actor, MessageKey.REVOKE_SKIPPED_SCOPES,
                        Placeholder.unparsed("scopes", this.formatAll(found.skipped())));
            }
        });
    }

    private static MessageKey failureKey(final RevokeResult.Failure.Reason reason) {
        return switch (reason) {
            case NOT_FOUND -> MessageKey.REVOKE_FAILURE_NOT_FOUND;
            case NOT_OWN -> MessageKey.REVOKE_FAILURE_NOT_OWN;
            case EXEMPT -> MessageKey.REVOKE_FAILURE_EXEMPT;
            case CANCELLED -> MessageKey.REVOKE_FAILURE_CANCELLED;
            case REASON_REQUIRED -> MessageKey.REVOKE_FAILURE_REASON_REQUIRED;
            case FORBIDDEN -> MessageKey.REVOKE_FAILURE_FORBIDDEN;
        };
    }
}
