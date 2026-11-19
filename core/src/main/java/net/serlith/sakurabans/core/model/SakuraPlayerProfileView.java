package net.serlith.sakurabans.core.model;

import java.net.InetAddress;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import net.serlith.sakurabans.api.model.Lengths;
import net.serlith.sakurabans.api.model.PlayerProfileView;

public record SakuraPlayerProfileView(UUID uuid, String name, Instant firstSeen, Instant lastSeen,
        Optional<InetAddress> lastAddress) implements PlayerProfileView {

    public SakuraPlayerProfileView {
        Objects.requireNonNull(uuid, "uuid");
        ModelChecks.name(name, Lengths.MAX_PLAYER_NAME, "name");
        ModelChecks.seen(firstSeen, lastSeen);
        Objects.requireNonNull(lastAddress, "lastAddress");
        lastAddress = lastAddress.map(address -> ModelChecks.address(address, "lastAddress"));
    }
}
