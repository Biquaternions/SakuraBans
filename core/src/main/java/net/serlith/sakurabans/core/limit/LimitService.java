package net.serlith.sakurabans.core.limit;

import java.time.Duration;
import java.time.Instant;
import java.time.InstantSource;
import java.util.Optional;
import net.serlith.sakurabans.api.model.Actor;
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
    public Prepared apply(final Prepared.Ready ready, final PunishOptions options) {
        final LimitSettings limits = this.config.limits();
        final Actor actor = ready.punishment().actor();
        if (options.bypassLimits() || limits.groups().isEmpty()
                || PermissionService.console(actor)
                || !(actor.operator() instanceof PlayerOperator player)) {
            return ready;
        }
        final LimitSettings.Group group = limits.groups().stream()
                .filter(candidate -> this.permissions.hasOnline(actor, candidate.permission()))
                .findFirst().orElse(null);
        if (group == null) {
            return ready;
        }
        return this.limit(ready, limits, group,
                this.permissions.hasOnline(actor, Permissions.LIMITS_UNLIMITED),
                this.permissions.hasOnline(actor, Permissions.COOLDOWN_BYPASS), player);
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
        return new Prepared.Ready(punishment.withExpiry(reduced));
    }

    private static boolean longer(final NewPunishment punishment, final Duration max) {
        return punishment.expiresAt() == null
                || Duration.between(punishment.createdAt(), punishment.expiresAt())
                        .compareTo(max) > 0;
    }

    @Override
    public void issued(final Punishment punishment) {
        if (punishment.operator() instanceof PlayerOperator actor) {
            this.cooldowns.issued(actor.uuid(), punishment.type(), punishment.createdAt());
        }
    }

}
