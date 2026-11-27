package net.serlith.sakurabans.core.model;

public enum SubjectType {
    PUNISHMENT,
    TEMPLATE,
    SETTINGS,
    MESSAGE,
    PLAYER,
    ALLOW_LIST,
    IMPORT,
    /**
     * An address without a player, by the address hash {@code sb_victims} stores (hex), never
     * the address itself (S64; a ladder applied to an address, its reset).
     */
    ADDRESS_HASH,
    /**
     * A victim by its {@code sb_victims} id: a range, or an address whose text was erased
     * (S64).
     */
    VICTIM
}
