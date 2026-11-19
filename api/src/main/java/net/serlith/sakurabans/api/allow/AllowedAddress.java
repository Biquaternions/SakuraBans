package net.serlith.sakurabans.api.allow;

import java.net.InetAddress;

/**
 * An allow-listed address, for example a shared school or café network. It links no accounts,
 * and address punishments of other accounts don't spread through it. Implemented by SakuraBans;
 * don't implement it.
 */
public non-sealed interface AllowedAddress extends AllowEntry {

    /**
     * The address.
     *
     * @return the address
     */
    InetAddress address();
}
