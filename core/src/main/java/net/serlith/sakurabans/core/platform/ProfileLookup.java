package net.serlith.sakurabans.core.platform;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

@FunctionalInterface
public interface ProfileLookup {

    ProfileLookup NONE = name -> CompletableFuture.completedFuture(Optional.empty());

    CompletableFuture<Optional<UUID>> uuid(String name);

}
