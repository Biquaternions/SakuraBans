package net.serlith.sakurabans.api.model;

import java.net.InetAddress;
import java.util.Objects;
import java.util.UUID;

/**
 * A player account together with one address. The punishment applies to a connection when the
 * account or the address matches.
 */
public non-sealed interface CompositeVictim extends Victim {

    /**
     * The account.
     *
     * @return the account's UUID
     */
    UUID uuid();

    /**
     * The address.
     *
     * @return the address, without host name or IPv6 scope id
     */
    InetAddress address();

    /**
     * Creates the victim. The address is normalised as in {@link AddressVictim#of(InetAddress)}.
     *
     * @param uuid the account's UUID
     * @param address the address
     * @return the victim
     */
    static CompositeVictim of(final UUID uuid, final InetAddress address) {
        record Impl(UUID uuid, InetAddress address) implements CompositeVictim {
            Impl {
                Objects.requireNonNull(uuid, "uuid");
                address = Checks.address(address, "address");
            }
        }

        return new Impl(uuid, address);
    }
}
