package net.serlith.sakurabans.core.permission;

import java.util.concurrent.CompletableFuture;
import net.serlith.sakurabans.api.model.Actor;
import net.serlith.sakurabans.api.model.CategoryScope;
import net.serlith.sakurabans.api.model.GlobalScope;
import net.serlith.sakurabans.api.model.PlayerOperator;
import net.serlith.sakurabans.api.model.Punishment;
import net.serlith.sakurabans.api.model.PunishmentType;
import net.serlith.sakurabans.api.model.Scope;
import net.serlith.sakurabans.api.model.ServerScope;
import net.serlith.sakurabans.core.configuration.ConfigService;
import net.serlith.sakurabans.core.model.ApiValues;
import net.serlith.sakurabans.core.storage.repository.PunishmentRepository;

public final class AuthorizationService {

    private final PermissionService permissions;
    private final ExemptionService exemption;
    private final PunishmentRepository punishments;
    private final ConfigService config;

    public AuthorizationService(final PermissionService permissions,
            final ExemptionService exemption, final PunishmentRepository punishments,
            final ConfigService config) {
        this.permissions = permissions;
        this.exemption = exemption;
        this.punishments = punishments;
        this.config = config;
    }

    public CompletableFuture<Decision> check(final Actor actor, final Action action) {
        return switch (action) {
            case Action.Punish punish -> CompletableFuture.completedFuture(
                    this.punish(actor, punish));
            case Action.ChangePunishment change -> this.change(actor, change);
            case Action.Manage manage -> CompletableFuture.completedFuture(
                    this.all(actor, switch (manage.area()) {
                        case TEMPLATES -> Permissions.ADMIN_TEMPLATES;
                        case SETTINGS -> Permissions.ADMIN_SETTINGS;
                        case MESSAGES -> Permissions.ADMIN_MESSAGES;
                        case PLAYER_DATA -> Permissions.ADMIN_GDPR;
                        case ROLLBACK -> Permissions.ADMIN_STAFFROLLBACK;
                    }));
        };
    }

    private Decision punish(final Actor actor, final Action.Punish punish) {
        if (!this.config.settings().scopes().requirePermissions()) {
            return this.all(actor, Permissions.punish(punish.type()));
        }
        return this.all(actor, Permissions.punish(punish.type()), punish.scope() == null
                ? Permissions.SCOPE_DEFAULT : scopeNode(punish.scope()));
    }

    private CompletableFuture<Decision> change(final Actor actor,
            final Action.ChangePunishment change) {
        return this.punishments.byId(change.id()).thenCompose(found -> {
            if (found.isEmpty()) {
                // The change itself reports NOT_FOUND.
                return CompletableFuture.completedFuture(Decision.ALLOWED);
            }
            final Punishment punishment = found.get();
            final PunishmentType type = punishment.type();
            final boolean own = ApiValues.sameOperator(punishment.operator(), actor.operator());
            final Decision allowed = switch (change.operation()) {
                case REVOKE -> this.any(actor, own, Permissions.revoke(type),
                        Permissions.revokeOwn(type));
                case EDIT, EVIDENCE -> this.any(actor, own, Permissions.edit(type),
                        Permissions.editOwn(type));
                case VOID -> this.all(actor, Permissions.voidPunishment(type));
                case EXPUNGE -> this.all(actor, Permissions.delete(type));
            };
            return allowed.allowed()
                    ? this.protectedIssuer(actor, punishment)
                    : CompletableFuture.completedFuture(allowed);
        });
    }

    private CompletableFuture<Decision> protectedIssuer(final Actor actor,
            final Punishment punishment) {
        if (!this.config.settings().exemption().protectsEdits()
                || !(punishment.operator() instanceof PlayerOperator issuer)) {
            return CompletableFuture.completedFuture(Decision.ALLOWED);
        }
        return this.exemption.isExempt(actor, issuer.uuid(), punishment.type())
                .thenApply(exempt -> exempt ? Decision.EXEMPT : Decision.ALLOWED);
    }

    private Decision any(final Actor actor, final boolean own, final String full,
            final String ownNode) {
        final Decision decision = this.all(actor, full);
        return decision.allowed() || !own ? decision : this.all(actor, ownNode);
    }

    private Decision all(final Actor actor, final String... nodes) {
        for (final String node : nodes) {
            if (!this.permissions.hasOnline(actor, node)) {
                return Decision.NO_PERMISSION;
            }
        }
        return Decision.ALLOWED;
    }

    private static String scopeNode(final Scope scope) {
        return switch (scope) {
            case GlobalScope global -> Permissions.SCOPE_GLOBAL;
            case ServerScope server -> Permissions.scopeServer(server.server());
            case CategoryScope category -> Permissions.scopeCategory(category.category());
        };
    }
}
