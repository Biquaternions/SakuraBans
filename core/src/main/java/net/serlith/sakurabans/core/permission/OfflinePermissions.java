package net.serlith.sakurabans.core.permission;

import java.util.Optional;
import java.util.OptionalInt;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public interface OfflinePermissions {

    OfflinePermissions NONE = new OfflinePermissions() {
        @Override
        public boolean available() {
            return false;
        }

        @Override
        public CompletableFuture<Optional<Boolean>> has(final UUID uuid, final String node) {
            return CompletableFuture.completedFuture(Optional.empty());
        }

        @Override
        public CompletableFuture<OptionalInt> weight(final UUID uuid) {
            return CompletableFuture.completedFuture(OptionalInt.empty());
        }
    };

    boolean available();

    CompletableFuture<Optional<Boolean>> has(UUID uuid, String node);

    CompletableFuture<OptionalInt> weight(UUID uuid);

}
