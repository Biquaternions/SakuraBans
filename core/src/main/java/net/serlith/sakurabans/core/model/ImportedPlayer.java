package net.serlith.sakurabans.core.model;

import com.google.common.base.Preconditions;
import java.net.InetAddress;
import java.time.Instant;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

public record ImportedPlayer(UUID uuid, @Nullable String name, @Nullable InetAddress address,
        Instant firstSeen, Instant lastSeen) {

    public ImportedPlayer {
        Preconditions.checkNotNull(uuid, "uuid");
        Preconditions.checkNotNull(firstSeen, "firstSeen");
        Preconditions.checkNotNull(lastSeen, "lastSeen");
        Preconditions.checkArgument(name != null || address != null,
                "an imported player has a name or an address");
        if (lastSeen.isBefore(firstSeen)) {
            lastSeen = firstSeen;
        }
    }

    public static ImportedPlayer named(final UUID uuid, final String name, final Instant seen) {
        return new ImportedPlayer(uuid, name, null, seen, seen);
    }

    public static ImportedPlayer at(final UUID uuid, final InetAddress address,
            final Instant seen) {
        return new ImportedPlayer(uuid, null, address, seen, seen);
    }
}
