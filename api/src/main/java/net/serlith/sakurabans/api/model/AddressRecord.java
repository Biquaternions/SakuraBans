package net.serlith.sakurabans.api.model;

import java.net.InetAddress;
import java.time.Instant;
import java.util.Optional;

/**
 * An address an account has joined from. Implemented by SakuraBans; don't implement it.
 */
public interface AddressRecord {

    /**
     * The address.
     *
     * @return the address, without host name or IPv6 scope id
     */
    InetAddress address();

    /**
     * When the account first joined from this address.
     *
     * @return the first sighting
     */
    Instant firstSeen();

    /**
     * When the account last joined from this address.
     *
     * @return the last sighting, not before {@link #firstSeen()}
     */
    Instant lastSeen();

    /**
     * The server of the last join from this address.
     *
     * @return the server name, or empty if unknown
     */
    Optional<String> lastServer();
}
