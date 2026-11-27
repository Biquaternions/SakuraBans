package net.serlith.sakurabans.core.permission;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;
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
    private final Function<UUID, Optional<String>> names;
    private final AtomicBoolean warned = new AtomicBoolean();

    /** {@code names}: the name this server knows for a player (the name cache); never blocks. */
    public ExemptionService(final ConfigService config, final PermissionService permissions,
            final ComponentLogger logger, final Function<UUID, Optional<String>> names) {
        this.config = config;
        this.permissions = permissions;
        this.logger = logger;
        this.names = names;
    }

    public CompletableFuture<Boolean> isExempt(final Actor actor, final UUID target,
            final PunishmentType type) {
        final Settings.Exemption exemption = this.config.settings().exemption();
        if (PermissionService.console(actor)
                || actor.operator() instanceof CustomOperator custom && custom.uuid().isEmpty()
                || actor.operator() instanceof PlayerOperator player
                        && player.uuid().equals(target)) {
            return CompletableFuture.completedFuture(false);
        }
        if (this.permissions.hasOnline(actor, Permissions.exemptBypass(type))) {
            return CompletableFuture.completedFuture(false);
        }
        if (this.listed(target, type)) {
            return CompletableFuture.completedFuture(true);
        }
        return this.inGroup(target, exemption).thenCompose(grouped -> grouped
                ? CompletableFuture.completedFuture(true)
                : this.byProvider(actor, target, type, exemption));
    }

    public boolean listed(final UUID target, final PunishmentType type) {
        return this.config.settings().exemption().listed(target,
                this.names.apply(target).orElse(null), type);
    }

    public CompletableFuture<Boolean> inGroup(final UUID target) {
        return this.inGroup(target, this.config.settings().exemption());
    }

    private CompletableFuture<Boolean> inGroup(final UUID target,
            final Settings.Exemption exemption) {
        if (exemption.groups().isEmpty()) {
            return CompletableFuture.completedFuture(false);
        }
        final List<CompletableFuture<Optional<Boolean>>> checks = new ArrayList<>();
        for (final String group : exemption.groups()) {
            checks.add(this.permissions.has(target, Permissions.luckPermsGroup(group)));
        }
        return CompletableFuture.allOf(checks.toArray(CompletableFuture[]::new))
                .thenApply(ignored -> checks.stream()
                        .anyMatch(check -> check.join().orElse(false)));
    }

    private CompletableFuture<Boolean> byProvider(final Actor actor, final UUID target,
            final PunishmentType type, final Settings.Exemption exemption) {
        if (exemption.provider() == ExemptionProvider.NONE) {
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
        // The target may be offline: its permission comes from LuckPerms then.
        return this.permissions.has(target, Permissions.exempt(type))
                .thenApply(exempt -> exempt.orElse(false));
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
