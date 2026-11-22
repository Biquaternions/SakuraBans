package net.serlith.sakurabans.core.enforce;

import java.time.Duration;
import java.time.InstantSource;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import net.kyori.adventure.text.logger.slf4j.ComponentLogger;
import net.serlith.sakurabans.api.event.PlayerDenyEvent.Denial;
import net.serlith.sakurabans.api.model.Punishment;
import net.serlith.sakurabans.api.model.PunishmentType;
import net.serlith.sakurabans.api.model.ServerScope;
import net.serlith.sakurabans.core.concurrent.Lifecycle;
import net.serlith.sakurabans.core.configuration.ConfigService;
import net.serlith.sakurabans.core.configuration.settings.Settings;
import net.serlith.sakurabans.core.event.SakuraEventBus;
import net.serlith.sakurabans.core.event.SakuraPlayerDenyEvent;
import net.serlith.sakurabans.core.model.LoginAttempt;
import net.serlith.sakurabans.core.notify.NotificationService;
import net.serlith.sakurabans.core.player.NameCache;
import net.serlith.sakurabans.core.punish.Applicabilities;
import net.serlith.sakurabans.core.punish.MuteCache;
import net.serlith.sakurabans.core.scope.SakuraScopeService;
import net.serlith.sakurabans.core.storage.repository.PlayerRepository;
import net.serlith.sakurabans.core.storage.repository.PunishmentRepository;
import net.serlith.sakurabans.core.util.Futures;

public final class LoginGate {

    private final Lifecycle lifecycle;
    private final ConfigService config;
    private final SakuraScopeService scopes;
    private final PunishmentRepository punishments;
    private final PlayerRepository players;
    private final NameCache names;
    private final MuteCache mutes;
    private final EnforcementMessages messages;
    private final NotificationService notifications;
    private final SakuraEventBus events;
    private final InstantSource clock;
    private final ComponentLogger logger;

    public LoginGate(final Lifecycle lifecycle, final ConfigService config,
            final SakuraScopeService scopes, final PunishmentRepository punishments,
            final PlayerRepository players, final NameCache names, final MuteCache mutes,
            final EnforcementMessages messages, final NotificationService notifications,
            final SakuraEventBus events, final InstantSource clock,
            final ComponentLogger logger) {
        this.lifecycle = lifecycle;
        this.config = config;
        this.scopes = scopes;
        this.punishments = punishments;
        this.players = players;
        this.names = names;
        this.mutes = mutes;
        this.messages = messages;
        this.notifications = notifications;
        this.events = events;
        this.clock = clock;
        this.logger = logger;
    }

    public CompletableFuture<LoginDecision> check(final LoginAttempt attempt) {
        if (!this.lifecycle.running()) {
            return CompletableFuture.completedFuture(new LoginDecision.Allowed());
        }
        final Settings settings = this.config.settings();
        this.names.put(attempt.uuid(), attempt.name());
        this.record(attempt, settings);
        if (!this.scopes.proxy()) {
            Futures.logged(this.mutes.load(attempt.uuid(), attempt.address()), this.logger,
                    "load the mutes of " + attempt.name());
        }
        return this.bounded(attempt, this.punishments.activeApplicable(PunishmentType.BAN,
                        Applicabilities.of(attempt.uuid(), attempt.address(),
                                settings.enforcement().addressStrictness()),
                        this.scopes.localScopes(), this.clock.instant())
                .thenApply(bans -> this.decide(attempt, bans, Denial.LOGIN_BANNED)))
                .whenComplete((decision, ignored) -> {
                    if (decision instanceof LoginDecision.Denied) {
                        this.mutes.invalidate(attempt.uuid());
                    }
                });
    }

    public CompletableFuture<LoginDecision> checkServer(final LoginAttempt attempt,
            final String server) {
        final Settings settings = this.config.settings();
        if (!this.lifecycle.running() || !settings.enforcement().enforceServerSwitch()) {
            return CompletableFuture.completedFuture(new LoginDecision.Allowed());
        }
        return this.bounded(attempt, this.punishments.activeApplicable(PunishmentType.BAN,
                        Applicabilities.of(attempt.uuid(), attempt.address(),
                                settings.enforcement().addressStrictness()),
                        this.scopes.scopesOn(server), this.clock.instant())
                .thenApply(bans -> this.decide(attempt, bans, Denial.SERVER_SWITCH_BANNED)));
    }

    public void quit(final UUID uuid) {
        this.mutes.invalidate(uuid);
    }

    private CompletableFuture<LoginDecision> bounded(final LoginAttempt attempt,
            final CompletableFuture<LoginDecision> decision) {
        final Duration timeout = this.config.settings().enforcement().loginCheckTimeout();
        return decision
                .orTimeout(timeout.toMillis(), TimeUnit.MILLISECONDS)
                .exceptionally(failure -> {
                    final Throwable cause = Futures.unwrap(failure);
                    if (cause instanceof TimeoutException) {
                        this.logger.warn("The login check of {} took longer than {} ms;"
                                + " letting them in", attempt.name(), timeout.toMillis());
                    } else {
                        this.logger.error("The login check of {} failed; letting them in",
                                attempt.name(), cause);
                    }
                    return new LoginDecision.Allowed();
                });
    }

    private LoginDecision decide(final LoginAttempt attempt, final List<Punishment> bans,
            final Denial denial) {
        if (bans.isEmpty()) {
            return new LoginDecision.Allowed();
        }
        final Punishment ban = bans.getFirst();
        final SakuraPlayerDenyEvent event = this.events.post(new SakuraPlayerDenyEvent(
                attempt.uuid(), attempt.name(), attempt.address(), denial, ban,
                this.messages.banScreen(ban, this.messages.defaultLocale())));
        if (event.cancelled()) {
            return new LoginDecision.Allowed();
        }
        this.notifications.attemptedJoin(attempt.name(), ban);
        return new LoginDecision.Denied(event.message(), denial, ban);
    }

    private void record(final LoginAttempt attempt, final Settings settings) {
        if (!settings.alts().registerOnConnection()) {
            return;
        }
        final String server = attempt.server() != null ? attempt.server()
                : this.scopes.thisServer().map(ServerScope::server).orElse(null);
        final boolean withAddress = server == null
                || !settings.alts().serversWithoutIpRegistration().contains(server);
        Futures.logged(this.players.recordConnection(attempt.uuid(), attempt.name(),
                this.clock.instant(), withAddress ? attempt.address() : null, server),
                this.logger, "record the connection of " + attempt.name());
    }

}
