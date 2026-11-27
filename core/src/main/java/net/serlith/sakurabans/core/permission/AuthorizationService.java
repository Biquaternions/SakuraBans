package net.serlith.sakurabans.core.permission;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.function.Predicate;
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
import org.jspecify.annotations.Nullable;

public final class AuthorizationService {

    private final PermissionService permissions;
    private final ExemptionService exemption;
    private final PunishmentRepository punishments;
    private final ConfigService config;
    private final TemplateGuard templates;

    public AuthorizationService(final PermissionService permissions,
            final ExemptionService exemption, final PunishmentRepository punishments,
            final ConfigService config, final TemplateGuard templates) {
        this.permissions = permissions;
        this.exemption = exemption;
        this.punishments = punishments;
        this.config = config;
        this.templates = templates;
    }

    /**
     * Whether the actor may do this. A player actor (or a custom operator with a UUID) who is
     * online is checked with their live permissions; one who isn't, with the offline
     * permissions (LuckPerms), and is denied without them. The console and a custom
     * operator without a UUID pass every permission check.
     */
    public CompletableFuture<Decision> check(final Actor actor, final Action action) {
        return switch (action) {
            case Action.Punish punish -> this.punish(actor, punish);
            case Action.ApplyLadder ladder -> this.ladder(actor, ladder);
            case Action.ChangePunishment change -> this.change(actor, change);
            case Action.Manage manage -> this.all(actor, switch (manage.area()) {
                case TEMPLATES -> Permissions.ADMIN_TEMPLATES;
                case SETTINGS -> Permissions.ADMIN_SETTINGS;
                case MESSAGES -> Permissions.ADMIN_MESSAGES;
                case PLAYER_DATA -> Permissions.ADMIN_GDPR;
                case ROLLBACK -> Permissions.ADMIN_STAFFROLLBACK;
            });
        };
    }

    private CompletableFuture<Decision> punish(final Actor actor, final Action.Punish punish) {
        final CompletableFuture<Decision> decision = punish.requiredTemplate() == null
                ? this.all(actor, Permissions.punish(punish.type()))
                : this.all(actor, Permissions.punish(punish.type()),
                        Permissions.template(punish.requiredTemplate()));
        return decision.thenCompose(first -> this.scoped(actor, first, punish.scope()));
    }

    private CompletableFuture<Decision> ladder(final Actor actor,
            final Action.ApplyLadder ladder) {
        final CompletableFuture<Decision> decision = ladder.permissionRequired()
                ? this.all(actor, Permissions.PUNISH, Permissions.template(ladder.ladder()))
                : this.all(actor, Permissions.PUNISH);
        return decision.thenCompose(first -> this.scoped(actor, first, ladder.scope()));
    }

    private CompletableFuture<Decision> scoped(final Actor actor, final Decision decision,
            final @Nullable Scope scope) {
        if (!decision.allowed() || !this.config.settings().scopes().requirePermissions()) {
            return CompletableFuture.completedFuture(decision);
        }
        return this.all(actor, scope == null ? Permissions.SCOPE_DEFAULT : scopeNode(scope));
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
                            : CompletableFuture.completedFuture(decision))
                    .thenCompose(decision -> decision.allowed()
                            ? this.templateNode(actor, punishment)
                            : CompletableFuture.completedFuture(decision));
        });
    }

    private CompletableFuture<Decision> templateNode(final Actor actor,
            final Punishment punishment) {
        final Optional<String> guarded = punishment.template().flatMap(this.templates::guarding);
        if (guarded.isEmpty()) {
            return CompletableFuture.completedFuture(Decision.ALLOWED);
        }
        return this.all(actor, Permissions.template(guarded.get())).thenApply(decision ->
                decision.allowed() ? decision : Decision.TEMPLATE_PROTECTED);
    }

    /**
     * Which existing punishments a punish with an override policy may end: those whose
     * template doesn't guard them, or whose template's node the actor has. Decided before the
     * insert, so the transaction asks only the cache.
     */
    public CompletableFuture<Predicate<Punishment>> replaceable(final Actor actor) {
        final Set<String> guarded = this.templates.guardedNames();
        if (guarded.isEmpty()) {
            return CompletableFuture.completedFuture(punishment -> true);
        }
        final List<String> names = List.copyOf(guarded);
        final List<CompletableFuture<Boolean>> checks = new ArrayList<>();
        for (final String name : names) {
            checks.add(this.permissions.hasAll(actor, List.of(Permissions.template(name))));
        }
        return CompletableFuture.allOf(checks.toArray(CompletableFuture[]::new))
                .thenApply(ignored -> {
                    final Set<String> allowed = new HashSet<>();
                    for (int i = 0; i < names.size(); i++) {
                        if (checks.get(i).join()) {
                            allowed.add(names.get(i));
                        }
                    }
                    return punishment -> punishment.template()
                            .flatMap(this.templates::guarding)
                            .map(allowed::contains)
                            .orElse(true);
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

    private CompletableFuture<Decision> any(final Actor actor, final boolean own,
            final String full, final String ownNode) {
        return this.all(actor, full).thenCompose(decision -> decision.allowed() || !own
                ? CompletableFuture.completedFuture(decision)
                : this.all(actor, ownNode));
    }

    private CompletableFuture<Decision> all(final Actor actor, final String... nodes) {
        return this.permissions.hasAll(actor, List.of(nodes))
                .thenApply(allowed -> allowed ? Decision.ALLOWED : Decision.NO_PERMISSION);
    }

    private static String scopeNode(final Scope scope) {
        return switch (scope) {
            case GlobalScope global -> Permissions.SCOPE_GLOBAL;
            case ServerScope server -> Permissions.scopeServer(server.server());
            case CategoryScope category -> Permissions.scopeCategory(category.category());
        };
    }
}
