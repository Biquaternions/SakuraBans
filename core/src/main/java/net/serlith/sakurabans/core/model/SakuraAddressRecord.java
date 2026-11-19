package net.serlith.sakurabans.core.model;

import java.net.InetAddress;
import java.time.Instant;
import java.util.Optional;
import net.serlith.sakurabans.api.model.AddressRecord;
import net.serlith.sakurabans.api.model.Lengths;
import org.jspecify.annotations.Nullable;

public record SakuraAddressRecord(
        InetAddress address, Instant firstSeen, Instant lastSeen,
        @Nullable String lastServerOrNull) implements AddressRecord {

    public SakuraAddressRecord {
        address = ModelChecks.address(address, "address");
        ModelChecks.seen(firstSeen, lastSeen);
        ModelChecks.optionalText(lastServerOrNull, Lengths.MAX_IDENTIFIER, "lastServer");
    }

    @Override
    public Optional<String> lastServer() {
        return Optional.ofNullable(this.lastServerOrNull);
    }
}
