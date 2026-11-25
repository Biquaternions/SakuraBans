package net.serlith.sakurabans.core.permission;

import java.util.Optional;
import java.util.OptionalInt;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import net.kyori.adventure.text.logger.slf4j.ComponentLogger;
import net.luckperms.api.LuckPerms;
import net.luckperms.api.LuckPermsProvider;
import net.luckperms.api.model.group.Group;
import net.luckperms.api.model.user.User;
import org.jspecify.annotations.Nullable;

public final class LuckPermsBridge implements OfflinePermissions {

    private static final String PROVIDER = "net.luckperms.api.LuckPermsProvider";

    private final ComponentLogger logger;
    private volatile @Nullable LuckPerms api;

    private LuckPermsBridge(final ComponentLogger logger) {
        this.logger = logger;
    }

    public static OfflinePermissions detect(final ComponentLogger logger) {
        try {
            Class.forName(PROVIDER, false, LuckPermsBridge.class.getClassLoader());
        } catch (final ClassNotFoundException | LinkageError e) {
            return OfflinePermissions.NONE;
        }
        return new LuckPermsBridge(logger);
    }

    private @Nullable LuckPerms api() {
        LuckPerms current = this.api;
        if (current == null) {
            try {
                current = LuckPermsProvider.get();
                this.api = current;
            } catch (final IllegalStateException e) {
                this.logger.warn("LuckPerms is installed but not loaded; offline permissions"
                        + " are unknown for now");
                return null;
            }
        }
        return current;
    }

    @Override
    public boolean available() {
        return this.api() != null;
    }

    @Override
    public CompletableFuture<Optional<Boolean>> has(final UUID uuid, final String node) {
        final LuckPerms luckPerms = this.api();
        if (luckPerms == null) {
            return CompletableFuture.completedFuture(Optional.empty());
        }
        return luckPerms.getUserManager().loadUser(uuid).thenApply(user -> Optional.of(
                user.getCachedData().getPermissionData().checkPermission(node).asBoolean()));
    }

    @Override
    public CompletableFuture<OptionalInt> weight(final UUID uuid) {
        final LuckPerms luckPerms = this.api();
        if (luckPerms == null) {
            return CompletableFuture.completedFuture(OptionalInt.empty());
        }
        return luckPerms.getUserManager().loadUser(uuid)
                .thenApply(user -> weightOf(luckPerms, user));
    }

    private static OptionalInt weightOf(final LuckPerms luckPerms, final User user) {
        return user.getInheritedGroups(user.getQueryOptions()).stream()
                .map(Group::getWeight)
                .filter(OptionalInt::isPresent)
                .mapToInt(OptionalInt::getAsInt)
                .max();
    }

}
