package net.serlith.sakurabans.api.allow;

import java.util.UUID;

/**
 * An allow-listed account. Address punishments and alt links don't reach it through its
 * addresses. Implemented by SakuraBans; don't implement it.
 */
public non-sealed interface AllowedPlayer extends AllowEntry {

    /**
     * The account.
     *
     * @return the account's UUID
     */
    UUID uuid();
}
