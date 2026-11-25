package net.serlith.sakurabans.core.limit;

import java.time.Duration;
import java.time.Instant;
import java.time.InstantSource;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import net.serlith.sakurabans.api.model.PlayerOperator;
import net.serlith.sakurabans.api.model.Punishment;
import net.serlith.sakurabans.api.punish.PunishOptions;
import net.serlith.sakurabans.api.punish.PunishResult.Failure.Reason;
import net.serlith.sakurabans.core.configuration.ConfigService;
import net.serlith.sakurabans.core.configuration.settings.LimitSettings;
import net.serlith.sakurabans.core.permission.PermissionService;
import net.serlith.sakurabans.core.permission.Permissions;
import net.serlith.sakurabans.core.punish.PunishLimits;
import net.serlith.sakurabans.core.punish.PunishmentFactory.Prepared;
import net.serlith.sakurabans.core.storage.repository.NewPunishment;
import org.jspecify.annotations.Nullable;

public final class LimitService implements PunishLimits {

    private final ConfigService config;
    private final PermissionService permissions;
    private final CooldownTracker cooldowns;
    private final InstantSource clock;

    public LimitService(final ConfigService config, final PermissionService permissions,
            final CooldownTracker cooldowns, final InstantSource clock) {
        this.config = config;
        this.permissions = permissions;
        this.cooldowns = cooldowns;
        this.clock = clock;
    }

    @Override
    public CompletableFuture<Prepared> apply(final Prepared.Ready ready,
            final PunishOptions options) {
        final LimitSettings limits = this.config.limits();
        if (options.bypassLimits() || limits.groups().isEmpty()
                || PermissionService.console(ready.punishment().actor())
                || !(ready.punishment().actor().operator() instanceof PlayerOperator)) {
            return CompletableFuture.completedFuture(ready);
        }
        final PlayerOperator actor = (PlayerOperator) ready.punishment().actor().operator();
        return this.group(actor, limits.groups(), 0).thenCompose(group -> {
            if (group == null) {
                return CompletableFuture.completedFuture(ready);
            }
            final CompletableFuture<Boolean> unlimited = this.has(actor,
                    Permissions.LIMITS_UNLIMITED);
            final CompletableFuture<Boolean> bypass = this.has(actor,
                    Permissions.COOLDOWN_BYPASS);
            return unlimited.thenCombine(bypass, (isUnlimited, mayBypass) ->
                    this.limit(ready, limits, group, isUnlimited, mayBypass, actor));
        });
    }

    private Prepared limit(final Prepared.Ready ready, final LimitSettings limits,
            final LimitSettings.Group group, final boolean unlimited, final boolean bypass,
            final PlayerOperator actor) {
        final NewPunishment punishment = ready.punishment();
        if (!unlimited && group.requireTemplate() && punishment.template() == null) {
            return new Prepared.Refused(Reason.TEMPLATE_REQUIRED);
        }
        final Optional<Duration> cooldown = group.cooldown(punishment.type());
        if (!bypass && cooldown.isPresent() && this.cooldowns.remaining(actor.uuid(),
                punishment.type(), cooldown.get(), this.clock.instant()).isPositive()) {
            return new Prepared.Refused(Reason.COOLDOWN);
        }
        final Optional<Duration> max = group.maxDuration(punishment.type());
        if (unlimited || max.isEmpty() || !longer(punishment, max.get())) {
            return ready;
        }
        if (!limits.reduceToLimit()) {
            return new Prepared.Refused(Reason.LIMIT_EXCEEDED);
        }
        final Instant reduced = punishment.createdAt().plus(max.get());
        return new Prepared.Ready(punishment.withExpiry(reduced), ready.kind());
    }

    private static boolean longer(final NewPunishment punishment, final Duration max) {
        return punishment.expiresAt() == null
                || Duration.between(punishment.createdAt(), punishment.expiresAt())
                        .compareTo(max) > 0;
    }

    private CompletableFuture<LimitSettings.@Nullable Group> group(final PlayerOperator actor,
            final List<LimitSettings.Group> groups, final int index) {
        if (index >= groups.size()) {
            return CompletableFuture.completedFuture(null);
        }
        final LimitSettings.Group group = groups.get(index);
        return this.has(actor, group.permission()).thenCompose(member -> member
                ? CompletableFuture.completedFuture(group)
                : this.group(actor, groups, index + 1));
    }

    private CompletableFuture<Boolean> has(final PlayerOperator actor, final String node) {
        return this.permissions.has(actor, node).thenApply(known -> known.orElse(false));
    }

    @Override
    public void issued(final Punishment punishment) {
        if (punishment.operator() instanceof PlayerOperator actor) {
            this.cooldowns.issued(actor.uuid(), punishment.type(), punishment.createdAt());
        }
    }

}
