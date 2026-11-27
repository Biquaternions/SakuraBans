package net.serlith.sakurabans.core.permission;

import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;
import net.kyori.adventure.text.logger.slf4j.ComponentLogger;
import net.serlith.sakurabans.api.model.Actor;
import net.serlith.sakurabans.api.model.ConsoleOperator;
import net.serlith.sakurabans.api.model.CustomOperator;
import net.serlith.sakurabans.api.model.PlayerOperator;
import net.serlith.sakurabans.core.platform.OnlinePlayer;
import net.serlith.sakurabans.core.platform.Platform;
import net.serlith.sakurabans.core.util.Futures;

public final class PermissionService {

    public static final String CONSOLE_SOURCE = "console";

    private final Platform platform;
    private final OfflinePermissions offline;
    private final Executor worker;
    private final ComponentLogger logger;
    private final AtomicBoolean warned = new AtomicBoolean();

    public PermissionService(final Platform platform, final OfflinePermissions offline,
            final Executor worker, final ComponentLogger logger) {
        this.platform = platform;
        this.offline = offline;
        this.worker = worker;
        this.logger = logger;
    }

    public static boolean console(final Actor actor) {
        return actor.operator() instanceof ConsoleOperator
                || CONSOLE_SOURCE.equals(actor.source());
    }

    public CompletableFuture<Optional<Boolean>> has(final UUID uuid, final String node) {
        final Optional<OnlinePlayer> online = this.platform.player(uuid);
        if (online.isPresent()) {
            return CompletableFuture.completedFuture(Optional.of(
                    online.get().hasPermission(node)));
        }
        this.warnIfMissing();
        return this.offline.has(uuid, node);
    }

    /**
     * Whether the actor has every node, for authorization. The console and a custom
     * operator without a UUID pass. A player (or a custom operator with a UUID) who is online
     * has their live permissions; one who isn't is asked of the offline permissions
     * (LuckPerms), on the worker, and is denied without LuckPerms or without an answer.
     */
    public CompletableFuture<Boolean> hasAll(final Actor actor, final List<String> nodes) {
        if (console(actor)) {
            return CompletableFuture.completedFuture(true);
        }
        final UUID uuid = switch (actor.operator()) {
            case ConsoleOperator console -> null;
            case PlayerOperator player -> player.uuid();
            case CustomOperator custom -> custom.uuid().orElse(null);
        };
        if (uuid == null) {
            return CompletableFuture.completedFuture(true);
        }
        final Optional<OnlinePlayer> online = this.platform.player(uuid);
        if (online.isPresent()) {
            return CompletableFuture.completedFuture(
                    nodes.stream().allMatch(online.get()::hasPermission));
        }
        if (!this.offline.available()) {
            this.warnIfMissing();
            return CompletableFuture.completedFuture(false);
        }
        return CompletableFuture.supplyAsync(() -> nodes.stream()
                        .map(node -> this.offline.has(uuid, node)).toList(), this.worker)
                .thenCompose(Futures::allOf)
                .thenApplyAsync(answers -> answers.stream()
                        .allMatch(answer -> answer.orElse(false)), this.worker);
    }

    public boolean hasOnline(final Actor actor, final String node) {
        if (console(actor)) {
            return true;
        }
        return switch (actor.operator()) {
            case ConsoleOperator console -> true;
            case PlayerOperator player -> this.hasOnline(player.uuid(), node);
            case CustomOperator custom -> custom.uuid()
                    .map(uuid -> this.hasOnline(uuid, node))
                    .orElse(true);
        };
    }

    private boolean hasOnline(final UUID uuid, final String node) {
        return this.platform.player(uuid).map(player -> player.hasPermission(node))
                .orElse(false);
    }

    public CompletableFuture<OptionalInt> weight(final UUID uuid) {
        this.warnIfMissing();
        return this.offline.weight(uuid);
    }

    public boolean weightsAvailable() {
        return this.offline.available();
    }

    private void warnIfMissing() {
        if (!this.offline.available() && this.warned.compareAndSet(false, true)) {
            this.logger.warn("Permissions of offline players are unknown without LuckPerms:"
                    + " the exemption of offline targets and the lockdown and GeoIP bypasses"
                    + " only see online players, and a staff member who isn't online (an API call"
                    + " on their behalf) is denied every action");
        }
    }

}
