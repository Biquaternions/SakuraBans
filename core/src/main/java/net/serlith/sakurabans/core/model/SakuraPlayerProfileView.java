package net.serlith.sakurabans.core.model;

import java.net.InetAddress;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import net.serlith.sakurabans.api.model.Lengths;
import net.serlith.sakurabans.api.model.PlayerProfileView;
import org.jspecify.annotations.Nullable;

public record SakuraPlayerProfileView(UUID uuid, String name, Instant firstSeen, Instant lastSeen,
        @Nullable InetAddress lastAddressOrNull) implements PlayerProfileView {

    public SakuraPlayerProfileView {
        Objects.requireNonNull(uuid, "uuid");
        ModelChecks.name(name, Lengths.MAX_PLAYER_NAME, "name");
        ModelChecks.seen(firstSeen, lastSeen);
        if (lastAddressOrNull != null) {
            lastAddressOrNull = ModelChecks.address(lastAddressOrNull, "lastAddress");
        }
    }

    @Override
    public Optional<InetAddress> lastAddress() {
        return Optional.ofNullable(this.lastAddressOrNull);
    }
}
