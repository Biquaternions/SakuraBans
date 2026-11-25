package net.serlith.sakurabans.api.data;

import java.util.UUID;

/**
 * What an erase removed or changed. Punishments and UUIDs are never removed by an erase; see
 * {@link PlayerDataService#erase(UUID, net.serlith.sakurabans.api.model.Actor)}. Implemented by
 * SakuraBans; don't implement it.
 */
public interface EraseResult {

    /**
     * The account.
     *
     * @return the account's UUID
     */
    UUID uuid();

    /**
     * Names deleted from its name history.
     *
     * @return the count
     */
    long namesRemoved();

    /**
     * Addresses deleted from its address history.
     *
     * @return the count
     */
    long addressesRemoved();

    /**
     * Allow-list entries deleted because their address was one of the account's addresses.
     *
     * @return the count
     */
    long allowedAddressesRemoved();

    /**
     * Victims whose address was cleared, keeping only its hash: the account's composite victims,
     * and address victims on one of its addresses that no other known account used. Their
     * punishments now name an {@link net.serlith.sakurabans.api.model.ErasedAddressVictim} and
     * still apply to that address.
     *
     * @return the count
     */
    long victimAddressesCleared();

    /**
     * Evidence links deleted from punishments naming the account.
     *
     * @return the count
     */
    long evidenceRemoved();

    /**
     * Punishments naming the account (as a player, composite or hashed victim), all kept.
     *
     * @return the count
     */
    long punishmentsKept();

    /**
     * Name snapshots of the account as staff (operator, revoker, voider, evidence and allow-list
     * adder, lockdown starter, audit actor) replaced with the text {@code erased}. Their UUIDs
     * stay.
     *
     * @return the number of rows changed
     */
    long namesRedacted();
}
