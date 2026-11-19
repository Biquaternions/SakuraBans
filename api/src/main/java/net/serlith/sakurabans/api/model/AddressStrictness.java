package net.serlith.sakurabans.api.model;

/**
 * How far address punishments reach to other accounts, and which accounts count as alts. Each
 * level includes the ones before it.
 */
public enum AddressStrictness {

    /** An address punishment applies to connections from that address only. */
    LENIENT,

    /** It also applies to accounts that have ever joined from the address. */
    NORMAL,

    /**
     * It also applies to accounts that share any address with such an account (one more hop).
     */
    STERN,

    /** Player punishments also apply to every address of the player, and so to their alts. */
    STRICT
}
