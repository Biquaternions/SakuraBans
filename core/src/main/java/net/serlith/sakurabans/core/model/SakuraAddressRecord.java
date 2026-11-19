package net.serlith.sakurabans.core.model;

import java.net.InetAddress;
import java.time.Instant;
import java.util.Optional;
import net.serlith.sakurabans.api.model.AddressRecord;
import net.serlith.sakurabans.api.model.Lengths;

public record SakuraAddressRecord(
        InetAddress address, Instant firstSeen, Instant lastSeen, Optional<String> lastServer)
        implements AddressRecord {

    public SakuraAddressRecord {
        address = ModelChecks.address(address, "address");
        ModelChecks.seen(firstSeen, lastSeen);
        ModelChecks.text(lastServer, Lengths.MAX_IDENTIFIER, "lastServer");
    }
}
