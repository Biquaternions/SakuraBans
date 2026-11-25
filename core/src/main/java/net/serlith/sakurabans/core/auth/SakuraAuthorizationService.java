package net.serlith.sakurabans.core.auth;

import com.google.common.base.Preconditions;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import net.kyori.adventure.text.logger.slf4j.ComponentLogger;
import net.serlith.sakurabans.api.auth.Action;
import net.serlith.sakurabans.api.auth.AuthorizationService;
import net.serlith.sakurabans.api.auth.Decision;
import net.serlith.sakurabans.api.integration.ActionGuard;
import net.serlith.sakurabans.api.model.Actor;
import net.serlith.sakurabans.api.model.CategoryScope;
import net.serlith.sakurabans.api.model.GlobalScope;
import net.serlith.sakurabans.api.model.PlayerOperator;
import net.serlith.sakurabans.api.model.Punishment;
import net.serlith.sakurabans.api.model.PunishmentType;
import net.serlith.sakurabans.api.model.Scope;
import net.serlith.sakurabans.api.model.ServerScope;
import net.serlith.sakurabans.core.configuration.ConfigService;
import net.serlith.sakurabans.core.integration.SakuraIntegrationRegistry;
import net.serlith.sakurabans.core.model.ApiValues;
import net.serlith.sakurabans.core.model.SakuraKind;
import net.serlith.sakurabans.core.permission.ExemptionService;
import net.serlith.sakurabans.core.permission.PermissionService;
import net.serlith.sakurabans.core.permission.Permissions;
import net.serlith.sakurabans.core.punish.ActionCheck;
import net.serlith.sakurabans.core.punish.BuiltInKinds;
import net.serlith.sakurabans.core.punish.KindLookup;
import net.serlith.sakurabans.core.storage.repository.PunishmentRepository;
import net.serlith.sakurabans.core.util.Futures;

public final class SakuraAuthorizationService implements AuthorizationService, ActionCheck {

    static final Duration GUARD_TIMEOUT = Duration.ofSeconds(3);

    private final PermissionService permissions;
    private final ExemptionService exemption;
    private final KindLookup kinds;
    private final PunishmentRepository punishments;
    private final SakuraIntegrationRegistry integrations;
    private final ConfigService config;
    private final ComponentLogger logger;

    public SakuraAuthorizationService(final PermissionService permissions,
            final ExemptionService exemption, final KindLookup kinds,
            final PunishmentRepository punishments, final SakuraIntegrationRegistry integrations,
            final ConfigService config, final ComponentLogger logger) {
        this.permissions = permissions;
        this.exemption = exemption;
        this.kinds = kinds;
        this.punishments = punishments;
        this.integrations = integrations;
        this.config = config;
        this.logger = logger;
    }

    @Override
    public CompletableFuture<Decision> check(final Actor actor, final Action action) {
        final Actor checked;
        try {
            checked = ApiValues.actor(actor);
            Preconditions.checkNotNull(action, "action");
        } catch (final RuntimeException e) {
            return CompletableFuture.failedFuture(e);
        }
        return this.ownRules(checked, action).thenCompose(decision -> decision.allowed()
                ? this.guards(checked, action)
                : CompletableFuture.completedFuture(decision));
    }

    private CompletableFuture<Decision> ownRules(final Actor actor, final Action action) {
        return switch (action) {
            case Action.Punish punish -> this.punish(actor, punish);
            case Action.ChangePunishment change -> this.change(actor, change);
            case Action.Rollback rollback -> this.all(actor, Permissions.ADMIN_STAFFROLLBACK);
            case Action.PlayerData data -> this.all(actor, switch (data.operation()) {
                case VIEW_ADDRESSES -> Permissions.VIEW_IPS;
                case FORGET_ADDRESS -> Permissions.ALTS_UNLINK;
                case EXPORT, ERASE -> Permissions.ADMIN_GDPR;
            });
            case Action.Manage manage -> this.all(actor, switch (manage.area()) {
                case KINDS -> Permissions.ADMIN_KINDS;
                case TEMPLATES -> Permissions.ADMIN_TEMPLATES;
                case SETTINGS -> Permissions.ADMIN_SETTINGS;
                case MESSAGES -> Permissions.ADMIN_MESSAGES;
                case ALLOW_LIST -> Permissions.ADMIN_ALLOW;
                case IMPORT -> Permissions.ADMIN_IMPORT;
            });
            case Action.View view -> this.all(actor, switch (view.area()) {
                case AUDIT -> Permissions.ADMIN_AUDIT;
                case STATS -> Permissions.ADMIN_STATS;
                case HIDDEN_PUNISHMENTS -> Permissions.VIEW_HIDDEN;
            });
        };
    }

    private CompletableFuture<Decision> punish(final Actor actor, final Action.Punish punish) {
        final SakuraKind kind = this.kinds.find(punish.kind()).orElse(null);
        if (kind == null) {
            // The punishment itself fails with UNKNOWN_KIND.
            return CompletableFuture.completedFuture(Decision.allow());
        }
        final List<String> nodes = new ArrayList<>();
        nodes.add(Permissions.punish(kind.baseType()));
        if (!kind.id().equals(BuiltInKinds.defaultFor(kind.baseType()))) {
            nodes.add(Permissions.kind(kind.id()));
        }
        if (this.config.settings().scopes().requirePermissions()) {
            nodes.add(punish.scope().map(SakuraAuthorizationService::scopeNode)
                    .orElse(Permissions.SCOPE_DEFAULT));
        }
        return this.all(actor, nodes.toArray(String[]::new));
    }

    private CompletableFuture<Decision> change(final Actor actor,
            final Action.ChangePunishment change) {
        return this.punishments.byId(change.id()).thenCompose(found -> {
            if (found.isEmpty()) {
                // The change itself reports NOT_FOUND.
                return CompletableFuture.completedFuture(Decision.allow());
            }
            final Punishment punishment = found.get();
            final PunishmentType type = punishment.type();
            final boolean own = ApiValues.sameOperator(punishment.operator(), actor.operator());
            final CompletableFuture<Decision> allowed = switch (change.operation()) {
                case REVOKE -> this.any(actor, own, Permissions.revoke(type),
                        Permissions.revokeOwn(type));
                case EDIT, EVIDENCE -> this.any(actor, own, Permissions.edit(type),
                        Permissions.editOwn(type));
                case VOID -> this.all(actor, Permissions.voidPunishment(type));
                case EXPUNGE -> this.all(actor, Permissions.delete(type));
            };
            return allowed.thenCompose(decision -> decision.allowed()
                    ? this.protectedIssuer(actor, punishment)
                    : CompletableFuture.completedFuture(decision));
        });
    }

    private CompletableFuture<Decision> protectedIssuer(final Actor actor,
            final Punishment punishment) {
        if (!this.config.settings().exemption().protectsEdits()
                || !(punishment.operator() instanceof PlayerOperator issuer)) {
            return CompletableFuture.completedFuture(Decision.allow());
        }
        return this.exemption.isExempt(actor, issuer.uuid(), punishment.type())
                .thenApply(exempt -> exempt
                        ? Decision.deny(Decision.Denial.EXEMPT, null)
                        : Decision.allow());
    }

    private CompletableFuture<Decision> any(final Actor actor, final boolean own,
            final String full, final String ownNode) {
        return this.all(actor, full).thenCompose(decision -> decision.allowed() || !own
                ? CompletableFuture.completedFuture(decision)
                : this.all(actor, ownNode));
    }

    private CompletableFuture<Decision> all(final Actor actor, final String... nodes) {
        final List<CompletableFuture<Optional<Boolean>>> checks = new ArrayList<>();
        for (final String node : nodes) {
            checks.add(this.permissions.has(actor, node));
        }
        return Futures.allOf(checks).thenApply(results -> {
            if (results.stream().anyMatch(Optional::isEmpty)) {
                return Decision.deny(Decision.Denial.UNKNOWN_PERMISSIONS, null);
            }
            return results.stream().allMatch(Optional::get)
                    ? Decision.allow()
                    : Decision.deny(Decision.Denial.NO_PERMISSION, null);
        });
    }

    private CompletableFuture<Decision> guards(final Actor actor, final Action action) {
        CompletableFuture<Decision> chain = CompletableFuture.completedFuture(Decision.allow());
        for (final ActionGuard guard : this.integrations.guards()) {
            chain = chain.thenCompose(decision -> decision.allowed()
                    ? this.ask(guard, actor, action)
                    : CompletableFuture.completedFuture(decision));
        }
        return chain;
    }

    private CompletableFuture<Decision> ask(final ActionGuard guard, final Actor actor,
            final Action action) {
        CompletableFuture<Decision> answer;
        try {
            answer = Preconditions.checkNotNull(guard.check(actor, action), "decision");
        } catch (final RuntimeException e) {
            answer = CompletableFuture.failedFuture(e);
        }
        return answer.toCompletableFuture()
                .orTimeout(GUARD_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS)
                .handle((decision, failure) -> {
                    if (failure != null || decision == null) {
                        this.logger.warn("The action guard {} failed; denying {}", guard.name(),
                                action, failure == null ? null : Futures.unwrap(failure));
                        return Decision.deny(Decision.Denial.GUARD, null);
                    }
                    return decision.allowed() ? decision
                            : Decision.deny(Decision.Denial.GUARD,
                                    decision.message().orElse(null));
                });
    }

    private static String scopeNode(final Scope scope) {
        return switch (scope) {
            case GlobalScope global -> Permissions.SCOPE_GLOBAL;
            case ServerScope server -> Permissions.scopeServer(server.server());
            case CategoryScope category -> Permissions.scopeCategory(category.category());
        };
    }

}
