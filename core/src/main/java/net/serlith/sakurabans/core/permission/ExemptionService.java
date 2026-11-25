package net.serlith.sakurabans.core.permission;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import net.kyori.adventure.text.logger.slf4j.ComponentLogger;
import net.serlith.sakurabans.api.model.Actor;
import net.serlith.sakurabans.api.model.CustomOperator;
import net.serlith.sakurabans.api.model.PlayerOperator;
import net.serlith.sakurabans.api.model.PunishmentType;
import net.serlith.sakurabans.core.configuration.ConfigService;
import net.serlith.sakurabans.core.configuration.settings.ExemptionProvider;
import net.serlith.sakurabans.core.configuration.settings.Settings;

public final class ExemptionService {

    private final ConfigService config;
    private final PermissionService permissions;
    private final ComponentLogger logger;
    private final AtomicBoolean warned = new AtomicBoolean();

    public ExemptionService(final ConfigService config, final PermissionService permissions,
            final ComponentLogger logger) {
        this.config = config;
        this.permissions = permissions;
        this.logger = logger;
    }

    public CompletableFuture<Boolean> isExempt(final Actor actor, final UUID target,
            final PunishmentType type) {
        final Settings.Exemption exemption = this.config.settings().exemption();
        if (exemption.provider() == ExemptionProvider.NONE || PermissionService.console(actor)
                || actor.operator() instanceof CustomOperator custom && custom.uuid().isEmpty()
                || actor.operator() instanceof PlayerOperator player
                        && player.uuid().equals(target)) {
            return CompletableFuture.completedFuture(false);
        }
        return this.permissions.has(actor, Permissions.exemptBypass(type)).thenCompose(bypass -> {
            if (bypass.orElse(false)) {
                return CompletableFuture.completedFuture(false);
            }
            if (exemption.provider() == ExemptionProvider.LUCKPERMS_WEIGHT) {
                if (this.permissions.weightsAvailable()) {
                    return this.byWeight(actor, target, exemption.permitSameWeight());
                }
                if (this.warned.compareAndSet(false, true)) {
                    this.logger.warn("exemption.provider is LUCKPERMS_WEIGHT but LuckPerms isn't"
                            + " installed; using PERMISSION instead");
                }
            }
            return this.permissions.has(target, Permissions.exempt(type))
                    .thenApply(exempt -> exempt.orElse(false));
        });
    }

    private CompletableFuture<Boolean> byWeight(final Actor actor, final UUID target,
            final boolean permitSameWeight) {
        final UUID actorUuid = switch (actor.operator()) {
            case PlayerOperator player -> player.uuid();
            case CustomOperator custom -> custom.uuid().orElseThrow();
            default -> throw new IllegalStateException("the console is never exempted from");
        };
        return this.permissions.weight(actorUuid).thenCombine(this.permissions.weight(target),
                (mine, theirs) -> {
                    final int actorWeight = mine.orElse(0);
                    final int targetWeight = theirs.orElse(0);
                    return targetWeight > actorWeight
                            || targetWeight == actorWeight && !permitSameWeight
                                    && theirs.isPresent();
                });
    }

}
