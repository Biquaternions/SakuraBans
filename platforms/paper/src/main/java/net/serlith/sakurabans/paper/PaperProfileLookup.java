package net.serlith.sakurabans.paper;

import com.destroystokyo.paper.profile.PlayerProfile;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import net.serlith.sakurabans.core.platform.ProfileLookup;
import org.bukkit.plugin.Plugin;

final class PaperProfileLookup implements ProfileLookup {

    private final Plugin plugin;

    PaperProfileLookup(final Plugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public CompletableFuture<Optional<UUID>> uuid(final String name) {
        final CompletableFuture<Optional<UUID>> result = new CompletableFuture<>();
        this.plugin.getServer().getAsyncScheduler().runNow(this.plugin, task -> {
            try {
                final PlayerProfile profile = this.plugin.getServer().createProfile(name);
                result.complete(profile.complete(false)
                        ? Optional.ofNullable(profile.getId())
                        : Optional.empty());
            } catch (final RuntimeException e) {
                result.completeExceptionally(e);
            }
        });
        return result;
    }
}
