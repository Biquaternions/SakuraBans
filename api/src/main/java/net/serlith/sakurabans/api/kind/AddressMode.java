package net.serlith.sakurabans.api.kind;

/**
 * How a kind treats the victim it is given.
 */
public enum AddressMode {

    /** Punish the victim as given. */
    AS_GIVEN,

    /** Punish the player's current address instead (an IP ban): a player victim needs one. */
    ADDRESS,

    /** Punish the player together with their current address (a composite victim). */
    COMPOSITE
}
