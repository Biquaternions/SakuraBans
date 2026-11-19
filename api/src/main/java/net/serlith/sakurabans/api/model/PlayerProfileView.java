package net.serlith.sakurabans.api.model;

import java.net.InetAddress;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * What SakuraBans knows about an account, as of when it was loaded. Implemented by SakuraBans;
 * don't implement it.
 */
public interface PlayerProfileView {

    /**
     * The account.
     *
     * @return the account's UUID
     */
    UUID uuid();

    /**
     * The last name seen.
     *
     * @return the name, at most {@link Lengths#MAX_PLAYER_NAME} characters
     */
    String name();

    /**
     * The first join.
     *
     * @return the first join
     */
    Instant firstSeen();

    /**
     * The last join.
     *
     * @return the last join, not before {@link #firstSeen()}
     */
    Instant lastSeen();

    /**
     * The address of the last join.
     *
     * @return the address, or empty if addresses aren't recorded
     */
    Optional<InetAddress> lastAddress();
}
