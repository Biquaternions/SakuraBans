package net.serlith.sakurabans.core.enforce;

import java.time.Instant;
import java.time.InstantSource;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.logger.slf4j.ComponentLogger;
import net.serlith.sakurabans.api.model.AddressRangeVictim;
import net.serlith.sakurabans.api.model.AddressStrictness;
import net.serlith.sakurabans.api.model.PlayerVictim;
import net.serlith.sakurabans.api.model.Punishment;
import net.serlith.sakurabans.api.model.PunishmentType;
import net.serlith.sakurabans.api.punish.PunishmentQuery.Applicability;
import net.serlith.sakurabans.api.template.TemplateDisplay;
import net.serlith.sakurabans.core.configuration.ConfigService;
import net.serlith.sakurabans.core.configuration.settings.Settings;
import net.serlith.sakurabans.core.platform.OnlinePlayer;
import net.serlith.sakurabans.core.platform.Platform;
import net.serlith.sakurabans.core.punish.ActiveRangeCache;
import net.serlith.sakurabans.core.punish.Enforcer;
import net.serlith.sakurabans.core.punish.MuteCache;
import net.serlith.sakurabans.core.punish.PunishmentCounts;
import net.serlith.sakurabans.core.scope.SakuraScopeService;
import net.serlith.sakurabans.core.storage.repository.PunishmentRepository;
import net.serlith.sakurabans.core.util.Futures;

public final class LocalEnforcer implements Enforcer {

    private final Platform platform;
    private final SakuraScopeService scopes;
    private final PunishmentRepository punishments;
    private final MuteCache mutes;
    private final EnforcementMessages messages;
    private final ConfigService config;
    private final InstantSource clock;
    private final ActiveRangeCache ranges;
    private final PunishmentCounts counts;
    private final ComponentLogger logger;

    public LocalEnforcer(final Platform platform, final SakuraScopeService scopes,
            final PunishmentRepository punishments, final MuteCache mutes,
            final EnforcementMessages messages, final ConfigService config,
            final InstantSource clock, final ActiveRangeCache ranges,
            final PunishmentCounts counts, final ComponentLogger logger) {
        this.platform = platform;
        this.scopes = scopes;
        this.punishments = punishments;
        this.mutes = mutes;
        this.messages = messages;
        this.config = config;
        this.clock = clock;
        this.ranges = ranges;
        this.counts = counts;
        this.logger = logger;
    }

    @Override
    public CompletableFuture<Void> created(final Punishment punishment) {
        final CompletableFuture<Void> work = this.rangesChanged(punishment)
                .thenCompose(ignored -> this.counted(punishment))
                .thenCompose(ignored -> this.affected(punishment))
                .thenCompose(targets -> {
                    final List<CompletableFuture<Void>> steps = new ArrayList<>();
                    for (final OnlinePlayer player : targets) {
                        // On a proxy, a server's punishments reach the players on that server.
                        if (this.scopes.appliesTo(punishment.scope(), player)) {
                            steps.add(this.apply(punishment, player));
                        } else if (punishment.type() == PunishmentType.MUTE
                                && this.scopes.proxy()) {
                            // Cached for when they switch there; told only where it applies.
                            steps.add(this.mutes.load(player.uuid(), player.address()));
                        }
                    }
                    return CompletableFuture.allOf(steps.toArray(CompletableFuture[]::new));
                });
        return quiet(Futures.logged(work, this.logger, "enforce punishment " + punishment.id()));
    }

    @Override
    public CompletableFuture<Void> ended(final Punishment punishment) {
        if (punishment.type() == PunishmentType.MUTE) {
            this.mutes.ended(punishment.id());
        }
        return quiet(Futures.logged(CompletableFuture.allOf(this.rangesChanged(punishment),
                this.counted(punishment)), this.logger, "reload the range punishments"));
    }

    @Override
    public CompletableFuture<Void> kickedUnrecorded(final UUID uuid, final String reason) {
        this.platform.player(uuid).ifPresent(player -> player.disconnect(
                this.messages.unrecordedKickScreen(player.name(), reason,
                        this.messages.localeOf(player))));
        return CompletableFuture.completedFuture(null);
    }

    public CompletableFuture<Void> joined(final UUID uuid) {
        final OnlinePlayer player = this.platform.player(uuid).orElse(null);
        if (player == null) {
            return CompletableFuture.completedFuture(null);
        }
        final CompletableFuture<Void> work = this.punishments
                .unacknowledgedWarnings(uuid, this.clock.instant())
                .thenCompose(warnings -> {
                    // One after another, oldest first, so they are shown in order.
                    CompletableFuture<Void> shown = CompletableFuture.completedFuture(null);
                    for (final Punishment warning : warnings) {
                        // Acknowledged first, so two instances can't both show it.
                        shown = shown.thenCompose(ignored -> this.punishments
                                .acknowledge(warning.id())
                                .thenAccept(mine -> {
                                    if (mine) {
                                        player.sendMessage(this.messages.warnedAway(warning,
                                                this.messages.localeOf(player)));
                                    }
                                }));
                    }
                    return shown;
                });
        return quiet(Futures.logged(work, this.logger,
                "show " + player.name() + " their warnings"));
    }

    @Override
    public CompletableFuture<Void> changed(final Punishment current, final boolean rescoped) {
        // An edit never changes the victim.
        final CompletableFuture<Void> ranges = CompletableFuture.allOf(
                this.rangesChanged(current), this.counted(current));
        final CompletableFuture<Void> work = ranges.thenCompose(ignored -> {
            if (!current.isActiveAt(this.clock.instant())) {
                return this.ended(current);
            }
            return switch (current.type()) {
                // Reloaded without a message: the player was told when it was issued.
                case MUTE -> CompletableFuture.allOf(this.platform.onlinePlayers().stream()
                        .map(player -> this.mutes.load(player.uuid(), player.address()))
                        .toArray(CompletableFuture[]::new));
                case BAN -> rescoped
                        ? this.created(current)
                        : CompletableFuture.completedFuture(null);
                case WARN, KICK -> CompletableFuture.completedFuture(null);
            };
        });
        return quiet(Futures.logged(work, this.logger, "apply the edit of " + current.id()));
    }

    // A count that can't be read leaves the old one; enforcement goes on.
    private CompletableFuture<Void> counted(final Punishment punishment) {
        return quiet(Futures.logged(this.counts.changed(punishment), this.logger,
                "count the punishments of the victim of " + punishment.id()));
    }

    private CompletableFuture<Void> rangesChanged(final Punishment punishment) {
        return punishment.victim() instanceof AddressRangeVictim
                ? this.ranges.reload()
                : CompletableFuture.completedFuture(null);
    }

    private CompletableFuture<Void> apply(final Punishment punishment, final OnlinePlayer player) {
        return switch (punishment.type()) {
            case BAN -> this.ban(punishment, player);
            case KICK -> {
                player.disconnect(this.messages.kickScreen(punishment,
                        this.messages.localeOf(player)));
                yield CompletableFuture.completedFuture(null);
            }
            case MUTE -> this.mutes.load(player.uuid(), player.address()).thenRun(() -> {
                if (!punishment.shadow()) {
                    this.notice(player, punishment, this.messages.muted(punishment,
                            this.messages.localeOf(player)));
                }
            });
            case WARN -> {
                this.notice(player, punishment, this.messages.warned(punishment,
                        this.messages.localeOf(player)));
                yield this.punishments.acknowledge(punishment.id()).thenRun(() -> { });
            }
        };
    }

    private void notice(final OnlinePlayer player, final Punishment punishment,
            final Component message) {
        if (this.messages.display(punishment) == TemplateDisplay.DIALOG) {
            final Locale locale = this.messages.localeOf(player);
            if (player.showNotice(this.messages.noticeTitle(punishment, locale),
                    this.messages.noticeBody(punishment, locale),
                    this.messages.noticeButton(locale))) {
                return;
            }
        }
        player.sendMessage(message);
    }

    private CompletableFuture<Void> ban(final Punishment punishment, final OnlinePlayer player) {
        final Locale locale = this.messages.localeOf(player);
        final Component screen = this.messages.banScreen(punishment, locale);
        final Settings.Enforcement enforcement = this.config.settings().enforcement();
        if (!this.scopes.proxy()) {
            if (!enforcement.loginCheck()) {
                player.sendMessage(this.messages.bannedAdmitted(punishment, locale));
            } else if (enforcement.networkKick()) {
                player.disconnectFromNetwork(screen);
            } else {
                player.disconnect(screen);
            }
            return CompletableFuture.completedFuture(null);
        }
        final String current = player.currentServer().orElse(null);
        if (current == null) {
            player.disconnect(screen);
            return CompletableFuture.completedFuture(null);
        }
        if (enforcement.withoutLoginCheck(current)) {
            player.sendMessage(this.messages.bannedAdmitted(punishment, locale));
            return CompletableFuture.completedFuture(null);
        }
        // The servers that take banned players first, in their order; then the proxy's
        // fallbacks where the ban doesn't apply. Each is tried until one connects; if none
        // does, the player is disconnected.
        final List<String> fallbacks = new ArrayList<>();
        for (final String server : enforcement.serversWithoutLoginCheck()) {
            if (!server.equalsIgnoreCase(current)) {
                fallbacks.add(server);
            }
        }
        this.scopes.fallbackServers().stream()
                .filter(server -> !server.equalsIgnoreCase(current))
                .filter(server -> !this.scopes.scopesOn(server).contains(punishment.scope()))
                .filter(server -> fallbacks.stream().noneMatch(server::equalsIgnoreCase))
                .forEach(fallbacks::add);
        return this.moveOrDisconnect(player, fallbacks, 0, screen,
                this.messages.bannedAdmitted(punishment, locale), enforcement);
    }

    private CompletableFuture<Void> moveOrDisconnect(final OnlinePlayer player,
            final List<String> fallbacks, final int index, final Component screen,
            final Component admitted, final Settings.Enforcement enforcement) {
        if (index >= fallbacks.size()) {
            player.disconnect(screen);
            return CompletableFuture.completedFuture(null);
        }
        final String target = fallbacks.get(index);
        return player.connect(target)
                .exceptionally(failure -> false)
                .thenCompose(connected -> {
                    if (connected) {
                        player.sendMessage(enforcement.withoutLoginCheck(target)
                                ? admitted : screen);
                        return CompletableFuture.<Void>completedFuture(null);
                    }
                    return this.moveOrDisconnect(player, fallbacks, index + 1, screen, admitted,
                            enforcement);
                });
    }

    private CompletableFuture<List<OnlinePlayer>> affected(final Punishment punishment) {
        final AddressStrictness strictness =
                this.config.settings().enforcement().addressStrictness();
        switch (punishment.victim()) {
            // Ranges aren't matched in SQL: the range cache decides, as at login.
            case AddressRangeVictim range -> {
                final Instant now = this.clock.instant();
                final List<CompletableFuture<Optional<OnlinePlayer>>> checks = new ArrayList<>();
                for (final OnlinePlayer player : this.platform.onlinePlayers()) {
                    checks.add(this.ranges.reaches(punishment, player.uuid(), player.address(),
                                    strictness, now)
                            .thenApply(reached -> reached
                                    ? Optional.of(player)
                                    : Optional.<OnlinePlayer>empty()));
                }
                return Futures.allOf(checks)
                        .thenApply(found -> found.stream().flatMap(Optional::stream).toList());
            }
            case PlayerVictim player when strictness != AddressStrictness.STRICT -> {
                return CompletableFuture.completedFuture(
                        this.platform.player(player.uuid()).stream().toList());
            }
            default -> {
                // The login query decides, so live enforcement and the next join agree.
            }
        }
        final Instant now = this.clock.instant();
        final List<CompletableFuture<Optional<OnlinePlayer>>> checks = new ArrayList<>();
        for (final OnlinePlayer player : this.platform.onlinePlayers()) {
            checks.add(this.punishments.reaches(punishment.id(),
                            Applicability.of(player.uuid(), player.address(), strictness), now)
                    .thenApply(reached -> reached
                            ? Optional.of(player)
                            : Optional.<OnlinePlayer>empty()));
        }
        return Futures.allOf(checks)
                .thenApply(found -> found.stream().flatMap(Optional::stream).toList());
    }

    private static CompletableFuture<Void> quiet(final CompletableFuture<Void> future) {
        return future.exceptionally(failure -> null);
    }
}
