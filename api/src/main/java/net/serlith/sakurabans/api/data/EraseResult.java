package net.serlith.sakurabans.api.data;

import java.util.UUID;

/**
 * What an erase removed or changed. Implemented by SakuraBans; don't implement it.
 */
public interface EraseResult {

    /**
     * The account.
     *
     * @return the account's UUID
     */
    UUID uuid();

    /**
     * Names deleted from its history.
     *
     * @return the count
     */
    long namesDeleted();

    /**
     * Addresses deleted from its history.
     *
     * @return the count
     */
    long addressesDeleted();

    /**
     * Ended punishments it received that were deleted.
     *
     * @return the count
     */
    long punishmentsExpunged();

    /**
     * Active punishments it received that stay in force with their personal data removed (name
     * snapshots, and the address of composite victims).
     *
     * @return the count
     */
    long punishmentsAnonymised();

    /**
     * Punishments it issued whose operator became an "erased" placeholder.
     *
     * @return the count
     */
    long issuedAnonymised();

    /**
     * Allow-list entries, appeals and evidence links deleted.
     *
     * @return the count
     */
    long otherDeleted();
}
