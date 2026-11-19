package net.serlith.sakurabans.api.model;

import java.net.InetAddress;

/**
 * One IP address as a victim.
 */
public non-sealed interface AddressVictim extends Victim {

    /**
     * The address.
     *
     * @return the address, without host name or IPv6 scope id
     */
    InetAddress address();

    /**
     * Creates the victim. The address is normalised: the host name and IPv6 scope id are dropped,
     * and an IPv4-mapped IPv6 address becomes IPv4.
     *
     * @param address the address
     * @return the victim
     */
    static AddressVictim of(final InetAddress address) {
        record Impl(InetAddress address) implements AddressVictim {
            Impl {
                address = Checks.address(address, "address");
            }
        }

        return new Impl(address);
    }
}
