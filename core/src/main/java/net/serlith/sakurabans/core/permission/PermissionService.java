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

    public CompletableFuture<Optional<Boolean>> has(final UUID uuid, final String node) {
        final Optional<OnlinePlayer> online = this.platform.player(uuid);
        if (online.isPresent()) {
            return CompletableFuture.completedFuture(Optional.of(
                    online.get().hasPermission(node)));
        }
        this.warnIfMissing();
        return this.offline.has(uuid, node);
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
                    + " the exemption of offline targets and the lockdown bypass only see online"
                    + " players");
        }
    }

}
