package net.serlith.sakurabans.core.enforce;

import java.time.Instant;
import java.time.InstantSource;
import java.util.ArrayList;
import java.util.List;
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
import net.serlith.sakurabans.core.configuration.ConfigService;
import net.serlith.sakurabans.core.messaging.ProxyChannel;
import net.serlith.sakurabans.core.platform.OnlinePlayer;
import net.serlith.sakurabans.core.platform.Platform;
import net.serlith.sakurabans.core.punish.ActiveRangeCache;
import net.serlith.sakurabans.core.punish.Enforcer;
import net.serlith.sakurabans.core.punish.MuteCache;
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
    private final ProxyChannel channel;
    private final InstantSource clock;
    private final ActiveRangeCache ranges;
    private final ComponentLogger logger;

    public LocalEnforcer(final Platform platform, final SakuraScopeService scopes,
            final PunishmentRepository punishments, final MuteCache mutes,
            final EnforcementMessages messages, final ConfigService config,
            final ProxyChannel channel, final InstantSource clock, final ActiveRangeCache ranges,
            final ComponentLogger logger) {
        this.platform = platform;
        this.scopes = scopes;
        this.punishments = punishments;
        this.mutes = mutes;
        this.messages = messages;
        this.config = config;
        this.channel = channel;
        this.clock = clock;
        this.ranges = ranges;
        this.logger = logger;
    }

    @Override
    public CompletableFuture<Void> created(final Punishment punishment) {
        final CompletableFuture<Void> work = this.rangesChanged(punishment)
                .thenCompose(ignored -> this.affected(punishment))
                .thenCompose(targets -> {
                    final List<CompletableFuture<Void>> steps = new ArrayList<>();
                    for (final OnlinePlayer player : targets) {
                        // On a proxy, a server's punishments reach the players on that server.
                        if (this.scopes.appliesTo(punishment.scope(), player)) {
                            steps.add(this.apply(punishment, player));
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
        return quiet(Futures.logged(this.rangesChanged(punishment), this.logger,
                "reload the range punishments"));
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
                        // Acknowledged first, so a proxy and a backend can't both show it.
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
    public CompletableFuture<Void> changed(final Punishment previous, final Punishment current) {
        final boolean ranged = previous.victim() instanceof AddressRangeVictim
                || current.victim() instanceof AddressRangeVictim;
        final CompletableFuture<Void> ranges = ranged
                ? this.ranges.reload()
                : CompletableFuture.completedFuture(null);
        final CompletableFuture<Void> work = ranges.thenCompose(ignored -> {
            if (!current.isActiveAt(this.clock.instant())) {
                return this.ended(current);
            }
            return switch (current.type()) {
                // Reloaded without a message: the player was told when it was issued.
                case MUTE -> this.scopes.proxy()
                        ? CompletableFuture.completedFuture(null)
                        : CompletableFuture.allOf(this.platform.onlinePlayers().stream()
                                .map(player -> this.mutes.load(player.uuid(), player.address()))
                                .toArray(CompletableFuture[]::new));
                case BAN -> previous.scope().equals(current.scope())
                        ? CompletableFuture.completedFuture(null)
                        : this.created(current);
                case WARN, KICK -> CompletableFuture.completedFuture(null);
            };
        });
        return quiet(Futures.logged(work, this.logger, "apply the edit of " + current.id()));
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
            // Mutes are enforced on the backends only (D4).
            case MUTE -> this.scopes.proxy()
                    ? CompletableFuture.completedFuture(null)
                    : this.mutes.load(player.uuid(), player.address()).thenRun(() -> {
                        if (!punishment.shadow()) {
                            player.sendMessage(this.messages.muted(punishment,
                                    this.messages.localeOf(player)));
                        }
                    });
            case WARN -> {
                player.sendMessage(this.messages.warned(punishment,
                        this.messages.localeOf(player)));
                yield this.punishments.acknowledge(punishment.id()).thenRun(() -> { });
            }
        };
    }

    private CompletableFuture<Void> ban(final Punishment punishment, final OnlinePlayer player) {
        final Component screen = this.messages.banScreen(punishment,
                this.messages.localeOf(player));
        if (!this.scopes.proxy()) {
            // Ask the proxy to apply it to the network (it may move them to another server).
            // Sent first: the kick would make the proxy move them before it reads the message.
            if (this.platform.serverIdentity().behindProxy()) {
                player.sendChannelMessage(this.channel.enforce(punishment.id()));
            }
            player.disconnect(screen);
            return CompletableFuture.completedFuture(null);
        }
        final String current = player.currentServer().orElse(null);
        if (current == null) {
            player.disconnect(screen);
            return CompletableFuture.completedFuture(null);
        }
        final List<String> fallbacks = this.scopes.fallbackServers().stream()
                .filter(server -> !server.equalsIgnoreCase(current))
                .filter(server -> !this.scopes.scopesOn(server).contains(punishment.scope()))
                .toList();
        return this.moveOrDisconnect(player, fallbacks, 0, screen);
    }

    private CompletableFuture<Void> moveOrDisconnect(final OnlinePlayer player,
            final List<String> fallbacks, final int index, final Component screen) {
        if (index >= fallbacks.size()) {
            player.disconnect(screen);
            return CompletableFuture.completedFuture(null);
        }
        return player.connect(fallbacks.get(index))
                .exceptionally(failure -> false)
                .thenCompose(connected -> {
                    if (connected) {
                        player.sendMessage(screen);
                        return CompletableFuture.<Void>completedFuture(null);
                    }
                    return this.moveOrDisconnect(player, fallbacks, index + 1, screen);
                });
    }

    private CompletableFuture<List<OnlinePlayer>> affected(final Punishment punishment) {
        final AddressStrictness strictness = punishment.strictness()
                .orElseGet(() -> this.config.settings().enforcement().addressStrictness());
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
