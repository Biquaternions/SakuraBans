package net.serlith.sakurabans.core.util;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Optional;
import java.util.regex.Pattern;

public final class InetAddresses {

    private static final Pattern IPV4 =
            Pattern.compile("(?:(?:25[0-5]|2[0-4]\\d|1\\d\\d|[1-9]?\\d)\\.){3}"
                    + "(?:25[0-5]|2[0-4]\\d|1\\d\\d|[1-9]?\\d)");

    private InetAddresses() {
    }

    public static Optional<InetAddress> parseLiteral(final String text) {
        if (text.indexOf(':') < 0 && !IPV4.matcher(text).matches()) {
            return Optional.empty();
        }
        try {
            return Optional.of(normalize(InetAddress.ofLiteral(text)));
        } catch (final IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    public static InetAddress normalize(final InetAddress address) {
        return fromBytes(address.getAddress());
    }

    public static byte[] toBytes(final InetAddress address) {
        return address.getAddress();
    }

    public static InetAddress fromBytes(final byte[] bytes) {
        if (bytes.length != 4 && bytes.length != 16) {
            throw new IllegalArgumentException("an address has 4 or 16 bytes, not " + bytes.length);
        }
        try {
            return InetAddress.getByAddress(bytes);
        } catch (final UnknownHostException e) {
            throw new IllegalArgumentException(e);
        }
    }

    /**
     * Hides the host part for players without the view-ips permission: {@code 203.0.*.*} and
     * {@code 2001:db8:*}.
     */
    public static String censor(final InetAddress address) {
        final byte[] bytes = address.getAddress();
        if (address instanceof Inet4Address) {
            return Byte.toUnsignedInt(bytes[0]) + "." + Byte.toUnsignedInt(bytes[1]) + ".*.*";
        }
        final int first = (Byte.toUnsignedInt(bytes[0]) << 8) | Byte.toUnsignedInt(bytes[1]);
        final int second = (Byte.toUnsignedInt(bytes[2]) << 8) | Byte.toUnsignedInt(bytes[3]);
        return Integer.toHexString(first) + ":" + Integer.toHexString(second) + ":*";
    }

}
