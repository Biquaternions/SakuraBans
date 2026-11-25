package net.serlith.sakurabans.core.permission;

import java.util.Optional;
import java.util.OptionalInt;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import net.kyori.adventure.text.logger.slf4j.ComponentLogger;
import net.serlith.sakurabans.api.model.Actor;
import net.serlith.sakurabans.api.model.ConsoleOperator;
import net.serlith.sakurabans.api.model.CustomOperator;
import net.serlith.sakurabans.api.model.Operator;
import net.serlith.sakurabans.api.model.PlayerOperator;
import net.serlith.sakurabans.core.platform.OnlinePlayer;
import net.serlith.sakurabans.core.platform.Platform;

public final class PermissionService {

    public static final String CONSOLE_SOURCE = "console";

    private final Platform platform;
    private final OfflinePermissions offline;
    private final ComponentLogger logger;
    private final AtomicBoolean warned = new AtomicBoolean();

    public PermissionService(final Platform platform, final OfflinePermissions offline,
            final ComponentLogger logger) {
        this.platform = platform;
        this.offline = offline;
        this.logger = logger;
    }

    public static boolean console(final Actor actor) {
        return actor.operator() instanceof ConsoleOperator
                || CONSOLE_SOURCE.equals(actor.source());
    }

    public CompletableFuture<Optional<Boolean>> has(final Actor actor, final String node) {
        return console(actor)
                ? CompletableFuture.completedFuture(Optional.of(true))
                : this.has(actor.operator(), node);
    }

    public CompletableFuture<Optional<Boolean>> has(final Operator operator, final String node) {
        return switch (operator) {
            case ConsoleOperator console -> CompletableFuture.completedFuture(Optional.of(true));
            case PlayerOperator player -> this.has(player.uuid(), node);
            // A plugin acting under its own name is trusted code.
            case CustomOperator custom -> custom.uuid()
                    .map(uuid -> this.has(uuid, node))
                    .orElseGet(() -> CompletableFuture.completedFuture(Optional.of(true)));
        };
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
                    + " exemption, limits and API authorization only see online players");
        }
    }

}
