package net.serlith.sakurabans.core.enforce;

import java.net.InetAddress;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Predicate;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.logger.slf4j.ComponentLogger;
import net.serlith.sakurabans.api.model.AddressRangeVictim;
import net.serlith.sakurabans.api.model.AddressStrictness;
import net.serlith.sakurabans.api.model.AddressVictim;
import net.serlith.sakurabans.api.model.CompositeVictim;
import net.serlith.sakurabans.api.model.PlayerProfileView;
import net.serlith.sakurabans.api.model.PlayerVictim;
import net.serlith.sakurabans.api.model.Punishment;
import net.serlith.sakurabans.api.model.PunishmentType;
import net.serlith.sakurabans.core.configuration.ConfigService;
import net.serlith.sakurabans.core.messaging.ProxyChannel;
import net.serlith.sakurabans.core.platform.OnlinePlayer;
import net.serlith.sakurabans.core.platform.Platform;
import net.serlith.sakurabans.core.punish.Enforcer;
import net.serlith.sakurabans.core.punish.MuteCache;
import net.serlith.sakurabans.core.scope.SakuraScopeService;
import net.serlith.sakurabans.core.storage.repository.PlayerRepository;
import net.serlith.sakurabans.core.util.Futures;

public final class LocalEnforcer implements Enforcer {

    private final Platform platform;
    private final SakuraScopeService scopes;
    private final PlayerRepository players;
    private final MuteCache mutes;
    private final EnforcementMessages messages;
    private final ConfigService config;
    private final ProxyChannel channel;
    private final ComponentLogger logger;

    public LocalEnforcer(final Platform platform, final SakuraScopeService scopes,
            final PlayerRepository players, final MuteCache mutes,
            final EnforcementMessages messages, final ConfigService config,
            final ProxyChannel channel, final ComponentLogger logger) {
        this.platform = platform;
        this.scopes = scopes;
        this.players = players;
        this.mutes = mutes;
        this.messages = messages;
        this.config = config;
        this.channel = channel;
        this.logger = logger;
    }

    @Override
    public CompletableFuture<Void> created(final Punishment punishment) {
        final CompletableFuture<Void> work = this.affected(punishment)
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
        return CompletableFuture.completedFuture(null);
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
                yield CompletableFuture.completedFuture(null);
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
        final Map<UUID, OnlinePlayer> found = new LinkedHashMap<>();
        final InetAddress address = switch (punishment.victim()) {
            case PlayerVictim player -> {
                this.platform.player(player.uuid()).ifPresent(p -> found.put(p.uuid(), p));
                yield null;
            }
            case CompositeVictim composite -> {
                this.platform.player(composite.uuid()).ifPresent(p -> found.put(p.uuid(), p));
                yield composite.address();
            }
            case AddressVictim victim -> victim.address();
            case AddressRangeVictim range -> {
                this.online(player -> range.contains(player.address()))
                        .forEach(p -> found.put(p.uuid(), p));
                yield null;
            }
        };
        if (address == null) {
            return CompletableFuture.completedFuture(List.copyOf(found.values()));
        }
        this.online(player -> player.address().equals(address))
                .forEach(p -> found.put(p.uuid(), p));
        final AddressStrictness strictness = punishment.strictness()
                .orElseGet(() -> this.config.settings().enforcement().addressStrictness());
        if (strictness == AddressStrictness.LENIENT) {
            return CompletableFuture.completedFuture(List.copyOf(found.values()));
        }
        return this.players.accountsAt(address).thenApply(accounts -> {
            for (final PlayerProfileView account : accounts) {
                this.platform.player(account.uuid()).ifPresent(p -> found.putIfAbsent(p.uuid(), p));
            }
            return List.copyOf(found.values());
        });
    }

    private List<? extends OnlinePlayer> online(final Predicate<OnlinePlayer> filter) {
        return this.platform.onlinePlayers().stream().filter(filter).toList();
    }

    private static CompletableFuture<Void> quiet(final CompletableFuture<Void> future) {
        return future.exceptionally(failure -> null);
    }
}
