package net.serlith.sakurabans.core.storage.repository;

import java.net.InetAddress;
import java.time.Duration;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public interface AltPolicy {

    AltPolicy NONE = new AltPolicy() {
        @Override
        public Set<UUID> allowedAccounts() {
            return Set.of();
        }

        @Override
        public Set<InetAddress> allowedAddresses() {
            return Set.of();
        }

        @Override
        public Optional<Duration> recordExpiration() {
            return Optional.empty();
        }
    };

    Set<UUID> allowedAccounts();

    Set<InetAddress> allowedAddresses();

    /** Address records not seen for this long don't count. */
    Optional<Duration> recordExpiration();

}
