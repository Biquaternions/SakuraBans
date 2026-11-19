package net.serlith.sakurabans.core.model;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.time.Instant;
import java.util.Locale;
import java.util.Objects;
import java.util.regex.Pattern;
import net.serlith.sakurabans.api.model.Lengths;
import org.jspecify.annotations.Nullable;

final class ModelChecks {

    private static final Pattern IDENTIFIER = Pattern.compile("[a-z0-9_-]+");
    private static final Pattern SOURCE = Pattern.compile("[a-z0-9_.:-]+");

    private ModelChecks() {
    }

    static String text(final String value, final int maxLength, final String what) {
        Objects.requireNonNull(value, what);
        if (value.length() > maxLength) {
            throw new IllegalArgumentException(
                    what + " is longer than " + maxLength + " characters");
        }
        return value;
    }

    static void optionalText(final @Nullable String value, final int maxLength,
            final String what) {
        if (value != null) {
            text(value, maxLength, what);
        }
    }

    static String name(final String value, final int maxLength, final String what) {
        text(value, maxLength, what);
        if (value.isBlank()) {
            throw new IllegalArgumentException(what + " must not be blank");
        }
        return value;
    }

    static String identifier(final String value, final String what) {
        Objects.requireNonNull(value, what);
        final String lower = value.toLowerCase(Locale.ROOT);
        if (lower.length() > Lengths.MAX_IDENTIFIER || !IDENTIFIER.matcher(lower).matches()) {
            throw new IllegalArgumentException(what + " must be 1-" + Lengths.MAX_IDENTIFIER
                    + " characters of a-z, 0-9, '_' or '-': '" + value + "'");
        }
        return lower;
    }

    static String source(final String value, final String what) {
        Objects.requireNonNull(value, what);
        final String lower = value.toLowerCase(Locale.ROOT);
        if (lower.length() > Lengths.MAX_SOURCE || !SOURCE.matcher(lower).matches()) {
            throw new IllegalArgumentException(what + " must be 1-" + Lengths.MAX_SOURCE
                    + " characters of a-z, 0-9, '_', '.', ':' or '-': '" + value + "'");
        }
        return lower;
    }

    static InetAddress address(final InetAddress address, final String what) {
        Objects.requireNonNull(address, what);
        try {
            return InetAddress.getByAddress(address.getAddress());
        } catch (final UnknownHostException e) {
            throw new IllegalArgumentException(what + " is not an IPv4 or IPv6 address", e);
        }
    }

    static void seen(final Instant firstSeen, final Instant lastSeen) {
        Objects.requireNonNull(firstSeen, "firstSeen");
        Objects.requireNonNull(lastSeen, "lastSeen");
        if (lastSeen.isBefore(firstSeen)) {
            throw new IllegalArgumentException("lastSeen must not be before firstSeen");
        }
    }
}
