package net.serlith.sakurabans.api.model;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Locale;
import java.util.Objects;
import java.util.regex.Pattern;

final class Checks {

    private static final Pattern NAME = Pattern.compile("[a-z0-9_-]+");

    private Checks() {
    }

    static InetAddress address(final InetAddress address, final String what) {
        Objects.requireNonNull(address, what);
        return fromBytes(address.getAddress());
    }

    static InetAddress fromBytes(final byte[] bytes) {
        try {
            // Drops the host name and any IPv6 scope id; IPv4-mapped addresses become IPv4.
            return InetAddress.getByAddress(bytes);
        } catch (final UnknownHostException e) {
            throw new IllegalArgumentException("not an IPv4 or IPv6 address", e);
        }
    }

    static String identifier(final String value, final String what) {
        Objects.requireNonNull(value, what);
        final String lower = value.toLowerCase(Locale.ROOT);
        if (lower.length() > Lengths.MAX_IDENTIFIER || !NAME.matcher(lower).matches()) {
            throw new IllegalArgumentException(what + " must be 1-" + Lengths.MAX_IDENTIFIER
                    + " characters of a-z, 0-9, '_' or '-': '" + value + "'");
        }
        return lower;
    }

    static String text(final String value, final int maxLength, final String what) {
        Objects.requireNonNull(value, what);
        if (value.length() > maxLength) {
            throw new IllegalArgumentException(
                    what + " is longer than " + maxLength + " characters");
        }
        return value;
    }

    static String name(final String value, final int maxLength, final String what) {
        text(value, maxLength, what);
        if (value.isBlank()) {
            throw new IllegalArgumentException(what + " must not be blank");
        }
        return value;
    }

}
