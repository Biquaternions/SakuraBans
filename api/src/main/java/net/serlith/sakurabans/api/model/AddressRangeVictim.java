package net.serlith.sakurabans.api.model;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.util.Objects;
import net.serlith.sakurabans.api.validation.FieldError;

/**
 * A range of IP addresses in CIDR form as a victim.
 */
public non-sealed interface AddressRangeVictim extends Victim {

    /**
     * The first address of the range.
     *
     * @return the network address; its host bits are zero
     */
    InetAddress network();

    /**
     * The number of network bits.
     *
     * @return 1-32 for IPv4, 1-128 for IPv6
     */
    int prefixLength();

    /**
     * Whether the address lies in this range. Addresses of the other family never do.
     *
     * @param address the address to test
     * @return {@code true} if the first {@link #prefixLength()} bits match
     */
    default boolean contains(final InetAddress address) {
        final byte[] candidate = Checks.address(address, "address").getAddress();
        final byte[] range = this.network().getAddress();
        if (candidate.length != range.length) {
            return false;
        }
        for (int bit = 0; bit < this.prefixLength(); bit++) {
            final int mask = 0x80 >>> (bit % Byte.SIZE);
            if ((candidate[bit / Byte.SIZE] & mask) != (range[bit / Byte.SIZE] & mask)) {
                return false;
            }
        }
        return true;
    }

    /**
     * Whether this is an IPv4 range.
     *
     * @return {@code true} for IPv4, {@code false} for IPv6
     */
    default boolean ipv4() {
        return this.network() instanceof Inet4Address;
    }

    /**
     * Creates the range. The prefix must fit the address family, and the host bits of
     * {@code network} are cleared, so {@code 10.1.2.3/8} becomes {@code 10.0.0.0/8}.
     *
     * @param network any address in the range
     * @param prefixLength the number of network bits
     * @return the victim
     */
    static AddressRangeVictim of(final InetAddress network, final int prefixLength) {
        record Impl(InetAddress network, int prefixLength) implements AddressRangeVictim {
            Impl {
                Objects.requireNonNull(network, "network");
                final byte[] bytes = network.getAddress();
                final int bits = bytes.length * Byte.SIZE;
                if (prefixLength < 1 || prefixLength > bits) {
                    throw Checks.error("prefixLength", FieldError.Code.OUT_OF_RANGE,
                            "prefix length " + prefixLength + " doesn't fit an "
                            + (bits == 32 ? "IPv4" : "IPv6") + " address (1-" + bits + ")");
                }
                for (int bit = prefixLength; bit < bits; bit++) {
                    bytes[bit / Byte.SIZE] &= (byte) ~(0x80 >>> (bit % Byte.SIZE));
                }
                network = Checks.fromBytes(bytes, "network");
            }
        }

        return new Impl(network, prefixLength);
    }
}
