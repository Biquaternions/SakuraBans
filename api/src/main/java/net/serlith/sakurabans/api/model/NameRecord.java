package net.serlith.sakurabans.api.model;

import java.time.Instant;

/**
 * A name an account has used. Implemented by SakuraBans; don't implement it.
 */
public interface NameRecord {

    /**
     * The name.
     *
     * @return the name, at most {@link Lengths#MAX_PLAYER_NAME} characters
     */
    String name();

    /**
     * When the account was first seen with this name.
     *
     * @return the first sighting
     */
    Instant firstSeen();

    /**
     * When the account was last seen with this name.
     *
     * @return the last sighting, not before {@link #firstSeen()}
     */
    Instant lastSeen();
}
