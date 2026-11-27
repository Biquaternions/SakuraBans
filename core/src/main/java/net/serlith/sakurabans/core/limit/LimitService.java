package net.serlith.sakurabans.core.limit;

import java.time.Duration;
import java.time.Instant;
import java.time.InstantSource;
import java.util.Optional;
import java.util.UUID;
import net.serlith.sakurabans.api.model.Actor;
import net.serlith.sakurabans.api.model.AddressRangeVictim;
import net.serlith.sakurabans.api.model.AddressVictim;
import net.serlith.sakurabans.api.model.CompositeVictim;
import net.serlith.sakurabans.api.model.ConsoleOperator;
import net.serlith.sakurabans.api.model.ErasedAddressVictim;
import net.serlith.sakurabans.api.model.PlayerOperator;
import net.serlith.sakurabans.api.model.PlayerVictim;
import net.serlith.sakurabans.api.model.Punishment;
import net.serlith.sakurabans.api.model.Victim;
import net.serlith.sakurabans.api.punish.PunishOptions;
import net.serlith.sakurabans.api.punish.PunishResult.Failure.Reason;
import net.serlith.sakurabans.core.configuration.ConfigService;
import net.serlith.sakurabans.core.configuration.settings.LimitSettings;
import net.serlith.sakurabans.core.model.ApiValues;
import net.serlith.sakurabans.core.permission.PermissionService;
import net.serlith.sakurabans.core.permission.Permissions;
import net.serlith.sakurabans.core.punish.PunishLimits;
import net.serlith.sakurabans.core.punish.PunishmentFactory.Origin;
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
        if (options.bypassLimits()) {
            return ready;
        }
        if (PermissionService.console(actor)) {
            return this.consoleCooldown(ready, limits);
        }
        if (limits.groups().isEmpty() || !(actor.operator() instanceof PlayerOperator player)) {
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
        // After lifting their own punishment of this victim, staff wait before punishing it again.
        if (!bypass && group.cooldownRedo().isPositive() && this.cooldowns.remainingRedo(
                actor.uuid(), victimKey(punishment.victim()), group.cooldownRedo(),
                this.clock.instant()).isPositive()) {
            return new Prepared.Refused(Reason.COOLDOWN);
        }
        final Optional<Duration> max = group.maxDuration(punishment.type());
        // A ladder's step decides its duration; the caps don't clip it.
        if (unlimited || ready.origin() == Origin.LADDER_STEP || max.isEmpty()
                || !longer(punishment, max.get())) {
            return ready;
        }
        if (!limits.reduceToLimit()) {
            return new Prepared.Refused(Reason.LIMIT_EXCEEDED);
        }
        final Instant reduced = punishment.createdAt().plus(max.get());
        return ready.withPunishment(punishment.withExpiry(reduced));
    }

    private Prepared consoleCooldown(final Prepared.Ready ready, final LimitSettings limits) {
        final NewPunishment punishment = ready.punishment();
        final Duration cooldown = limits.consoleCooldown();
        if (cooldown.isPositive() && this.cooldowns.remainingConsole(
                victimKey(punishment.victim()), punishment.type(), cooldown,
                this.clock.instant()).isPositive()) {
            return new Prepared.Refused(Reason.COOLDOWN);
        }
        return ready;
    }

    private static String victimKey(final Victim victim) {
        return switch (victim) {
            case PlayerVictim player -> player.uuid().toString();
            case CompositeVictim composite -> composite.uuid().toString();
            case AddressVictim address -> address.address().getHostAddress();
            case AddressRangeVictim range ->
                    range.network().getHostAddress() + "/" + range.prefixLength();
            case ErasedAddressVictim erased -> erased.uuid().map(UUID::toString)
                    .orElseGet(erased::hash);
        };
    }

    private static boolean longer(final NewPunishment punishment, final Duration max) {
        return punishment.expiresAt() == null
                || Duration.between(punishment.createdAt(), punishment.expiresAt())
                        .compareTo(max) > 0;
    }

    @Override
    public void issued(final Punishment punishment) {
        if (punishment.operator() instanceof ConsoleOperator
                || PermissionService.CONSOLE_SOURCE.equals(punishment.source())) {
            this.cooldowns.consolePunished(victimKey(punishment.victim()), punishment.type(),
                    punishment.createdAt());
        } else if (punishment.operator() instanceof PlayerOperator actor) {
            this.cooldowns.issued(actor.uuid(), punishment.type(), punishment.createdAt());
        }
    }

    @Override
    public void revoked(final Punishment punishment, final Actor actor) {
        if (actor.operator() instanceof PlayerOperator player
                && ApiValues.sameOperator(punishment.operator(), player)) {
            this.cooldowns.revokedOwn(player.uuid(), victimKey(punishment.victim()),
                    this.clock.instant());
        }
    }

}
