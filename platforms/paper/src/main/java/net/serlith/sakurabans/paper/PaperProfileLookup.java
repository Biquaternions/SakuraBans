package net.serlith.sakurabans.paper;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import net.serlith.sakurabans.core.platform.ProfileLookup;
import org.bukkit.Server;

final class PaperProfileLookup implements ProfileLookup {

    private final Server server;

    PaperProfileLookup(final Server server) {
        this.server = server;
    }

    @Override
    public CompletableFuture<Optional<UUID>> uuid(final String name) {
        // update() runs on Paper's own executor and never blocks the caller.
        return this.server.createProfile(name).update()
                .thenApply(profile -> Optional.ofNullable(profile.getId())
                        .filter(uuid -> profile.isComplete()));
    }

}
