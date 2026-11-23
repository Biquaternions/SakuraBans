package net.serlith.sakurabans.core.model;

import com.google.common.base.Preconditions;
import java.net.InetAddress;
import java.time.Instant;
import java.util.Optional;
import net.serlith.sakurabans.api.allow.AllowedAddress;
import net.serlith.sakurabans.api.model.Actor;
import org.jspecify.annotations.Nullable;

public record SakuraAllowedAddress(InetAddress address, Actor addedBy, Instant addedAt,
        @Nullable String noteOrNull) implements AllowedAddress {

    public SakuraAllowedAddress {
        address = ModelChecks.address(address, "address");
        Preconditions.checkNotNull(addedBy, "addedBy");
        Preconditions.checkNotNull(addedAt, "addedAt");
    }

    @Override
    public Optional<String> note() {
        return Optional.ofNullable(this.noteOrNull);
    }
}
