package net.serlith.sakurabans.core.enforce;

import java.time.Duration;
import java.time.Instant;
import java.time.InstantSource;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.logger.slf4j.ComponentLogger;
import net.serlith.sakurabans.api.event.PlayerDenyEvent.Denial;
import net.serlith.sakurabans.api.model.AddressStrictness;
import net.serlith.sakurabans.api.model.Punishment;
import net.serlith.sakurabans.api.model.PunishmentType;
import net.serlith.sakurabans.api.model.Scope;
import net.serlith.sakurabans.api.model.ServerScope;
import net.serlith.sakurabans.core.alts.AltService;
import net.serlith.sakurabans.core.alts.SakuraAllowListService;
import net.serlith.sakurabans.core.concurrent.Lifecycle;
import net.serlith.sakurabans.core.configuration.ConfigService;
import net.serlith.sakurabans.core.configuration.settings.Settings;
import net.serlith.sakurabans.core.event.SakuraEventBus;
import net.serlith.sakurabans.core.event.SakuraPlayerDenyEvent;
import net.serlith.sakurabans.core.model.LoginAttempt;
import net.serlith.sakurabans.core.notify.NotificationService;
import net.serlith.sakurabans.core.player.NameCache;
import net.serlith.sakurabans.core.punish.ActiveRangeCache;
import net.serlith.sakurabans.core.punish.Applicabilities;
import net.serlith.sakurabans.core.punish.MuteCache;
import net.serlith.sakurabans.core.scope.SakuraScopeService;
import net.serlith.sakurabans.core.storage.repository.PlayerRepository;
import net.serlith.sakurabans.core.storage.repository.PunishmentRepository;
import net.serlith.sakurabans.core.util.Durations;
import net.serlith.sakurabans.core.util.Futures;
import org.jspecify.annotations.Nullable;

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
    private final SakuraAllowListService allowList;
    private final AltService alts;
    private final ActiveRangeCache ranges;
    private final ComponentLogger logger;

    public LoginGate(final Lifecycle lifecycle, final ConfigService config,
            final SakuraScopeService scopes, final PunishmentRepository punishments,
            final PlayerRepository players, final NameCache names, final MuteCache mutes,
            final EnforcementMessages messages, final NotificationService notifications,
            final SakuraEventBus events, final InstantSource clock,
            final SakuraAllowListService allowList, final AltService alts,
            final ActiveRangeCache ranges, final ComponentLogger logger) {
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
        this.allowList = allowList;
        this.alts = alts;
        this.ranges = ranges;
        this.logger = logger;
    }

    public CompletableFuture<LoginDecision> check(final LoginAttempt attempt) {
        if (!this.lifecycle.running()) {
            return CompletableFuture.completedFuture(this.unchecked(attempt,
                    Denial.LOGIN_CHECK_FAILED));
        }
        final Settings settings = this.config.settings();
        this.names.put(attempt.uuid(), attempt.name());
        if (!this.scopes.proxy()) {
            Futures.logged(this.mutes.load(attempt.uuid(), attempt.address()), this.logger,
                    "load the mutes of " + attempt.name());
        }
        return this.bounded(attempt, Denial.LOGIN_CHECK_FAILED, this.limited(attempt, settings)
                .thenCompose(limited -> {
                    if (limited.isPresent()) {
                        return CompletableFuture.completedFuture(limited.get());
                    }
                    // Only after the limiter, which would otherwise count this account itself.
                    // A proxy records the address when the player reaches a server.
                    this.record(attempt, settings, !this.scopes.proxy());
                    if (!settings.enforcement().denyBannedJoins()) {
                        return CompletableFuture.completedFuture(new LoginDecision.Allowed());
                    }
                    return this.bans(attempt, settings, this.scopes.localScopes())
                            .thenApply(bans -> this.decide(attempt, bans, Denial.LOGIN_BANNED,
                                    null));
                }))
                .whenComplete((decision, ignored) -> {
                    if (decision instanceof LoginDecision.Denied) {
                        this.mutes.invalidate(attempt.uuid());
                    } else if (decision != null) {
                        this.noticeAlts(attempt);
                    }
                });
    }

    private CompletableFuture<Optional<LoginDecision>> limited(final LoginAttempt attempt,
            final Settings settings) {
        final Settings.ConnectionLimiter limiter = settings.alts().connectionLimiter();
        if (!limiter.enabled() || this.allowList.isAllowed(attempt.uuid())
                || this.allowList.isAllowed(attempt.address())) {
            return CompletableFuture.completedFuture(Optional.empty());
        }
        return this.players.seenAt(attempt.address(), this.clock.instant().minus(limiter.window()))
                .thenApply(seen -> {
                    if (seen.contains(attempt.uuid()) || seen.size() < limiter.limit()) {
                        return Optional.empty();
                    }
                    this.logger.info("{} was turned away: {} other accounts joined from their"
                            + " address within {}", attempt.name(), seen.size(),
                            Durations.formatCompact(limiter.window()));
                    return Optional.of(this.denied(attempt, Denial.LOGIN_CONNECTION_LIMIT,
                            this.messages.connectionLimit(this.messages.defaultLocale())));
                });
    }

    private void noticeAlts(final LoginAttempt attempt) {
        final Settings settings = this.config.settings();
        final Settings.JoinNotification join = settings.alts().joinNotification();
        if (!join.enabled()) {
            return;
        }
        Futures.logged(this.alts.alts(attempt.uuid(), settings.enforcement().addressStrictness())
                .thenAccept(found -> {
                    if (found.size() >= join.threshold()
                            && (!join.onlyIfPunished() || AltService.anyPunished(found))) {
                        this.notifications.joinedWithAlts(attempt.uuid(), attempt.name(), found,
                                join.limit());
                    }
                }), this.logger, "look up the alts of " + attempt.name());
    }

    public void connected(final LoginAttempt attempt) {
        if (this.lifecycle.running()) {
            this.record(attempt, this.config.settings(), true);
        }
    }

    public CompletableFuture<LoginDecision> checkServer(final LoginAttempt attempt,
            final String server) {
        final Settings settings = this.config.settings();
        if (!settings.enforcement().enforceServerSwitch()) {
            return CompletableFuture.completedFuture(new LoginDecision.Allowed());
        }
        if (!this.lifecycle.running()) {
            return CompletableFuture.completedFuture(this.unchecked(attempt,
                    Denial.SERVER_SWITCH_CHECK_FAILED));
        }
        final List<String> fallbacks = this.scopes.fallbackServers().stream()
                .filter(fallback -> !fallback.equalsIgnoreCase(server))
                .toList();
        final Set<Scope> scopes = new HashSet<>(this.scopes.scopesOn(server));
        fallbacks.forEach(fallback -> scopes.addAll(this.scopes.scopesOn(fallback)));
        return this.bounded(attempt, Denial.SERVER_SWITCH_CHECK_FAILED,
                this.bans(attempt, settings, scopes)
                .thenApply(bans -> {
                    final List<Punishment> here = this.on(server, bans);
                    final String fallback = fallbacks.stream()
                            .filter(candidate -> this.on(candidate, bans).isEmpty())
                            .findFirst()
                            .orElse(null);
                    return this.decide(attempt, here, Denial.SERVER_SWITCH_BANNED, fallback);
                }));
    }

    private CompletableFuture<List<Punishment>> bans(final LoginAttempt attempt,
            final Settings settings, final Set<Scope> scopes) {
        final AddressStrictness strictness = settings.enforcement().addressStrictness();
        final Instant now = this.clock.instant();
        return this.punishments.activeApplicable(PunishmentType.BAN,
                        Applicabilities.of(attempt.uuid(), attempt.address(), strictness),
                        scopes, now)
                .thenCompose(found -> this.ranges.matching(PunishmentType.BAN, attempt.uuid(),
                                attempt.address(), strictness, scopes, now)
                        .thenApply(ranged -> {
                            final List<Punishment> all = new ArrayList<>(found);
                            all.addAll(ranged);
                            return List.copyOf(all);
                        }));
    }

    private List<Punishment> on(final String server, final List<Punishment> bans) {
        final Set<Scope> scopes = this.scopes.scopesOn(server);
        return bans.stream().filter(ban -> scopes.contains(ban.scope())).toList();
    }

    public void quit(final UUID uuid) {
        this.mutes.invalidate(uuid);
    }

    private CompletableFuture<LoginDecision> bounded(final LoginAttempt attempt,
            final Denial denial, final CompletableFuture<LoginDecision> decision) {
        final Duration timeout = this.config.settings().enforcement().loginCheckTimeout();
        return decision
                .orTimeout(timeout.toMillis(), TimeUnit.MILLISECONDS)
                .exceptionally(failure -> {
                    final Throwable cause = Futures.unwrap(failure);
                    if (cause instanceof TimeoutException) {
                        this.logger.warn("The ban check of {} took longer than {} ms;"
                                + " turning them away", attempt.name(), timeout.toMillis());
                    } else {
                        this.logger.error("The ban check of {} failed; turning them away",
                                attempt.name(), cause);
                    }
                    return this.unchecked(attempt, denial);
                });
    }

    private LoginDecision unchecked(final LoginAttempt attempt, final Denial denial) {
        return this.denied(attempt, denial,
                this.messages.checkFailed(this.messages.defaultLocale()));
    }

    private LoginDecision denied(final LoginAttempt attempt, final Denial denial,
            final Component screen) {
        final SakuraPlayerDenyEvent event = this.events.post(new SakuraPlayerDenyEvent(
                attempt.uuid(), attempt.name(), attempt.address(), denial, null, screen));
        if (event.cancelled()) {
            return new LoginDecision.Allowed();
        }
        return new LoginDecision.Denied(event.message(), denial, null);
    }

    private LoginDecision decide(final LoginAttempt attempt, final List<Punishment> bans,
            final Denial denial, final @Nullable String fallback) {
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
        return new LoginDecision.Denied(event.message(), denial, ban, fallback);
    }

    private void record(final LoginAttempt attempt, final Settings settings,
            final boolean addressAllowed) {
        if (!settings.alts().registerOnConnection()) {
            return;
        }
        final String server = attempt.server() != null ? attempt.server()
                : this.scopes.thisServer().map(ServerScope::server).orElse(null);
        final boolean withAddress = addressAllowed && (server == null
                || !settings.alts().serversWithoutIpRegistration().contains(server));
        Futures.logged(this.players.recordConnection(attempt.uuid(), attempt.name(),
                this.clock.instant(), withAddress ? attempt.address() : null, server),
                this.logger, "record the connection of " + attempt.name());
    }

}
