package net.serlith.sakurabans.api.model;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Locale;
import java.util.Objects;
import java.util.regex.Pattern;
import net.serlith.sakurabans.api.validation.FieldError.Code;
import net.serlith.sakurabans.api.validation.Validator;

final class Checks {

    private static final Pattern IDENTIFIER = Pattern.compile("[a-z0-9_-]+");
    private static final Pattern SOURCE = Pattern.compile("[a-z0-9_.:-]+");
    static final Pattern EVIDENCE_URL = Pattern.compile("https?://\\S+");
    static final Pattern EVIDENCE_REFERENCE =
            Pattern.compile("([a-z0-9_-]+):([a-z0-9_-]+):([A-Za-z0-9_.-]+)");

    private Checks() {
    }

    // Throws the ValidationException itself; the return type only lets callers write
    // "throw Checks.error(...)" so the compiler sees the branch ends.
    static RuntimeException error(final String path, final Code code, final String message) {
        Validator.create().add(path, code, message).throwIfAny();
        throw new IllegalStateException("unreachable");
    }

    static InetAddress address(final InetAddress address, final String path) {
        Objects.requireNonNull(address, path);
        return fromBytes(address.getAddress(), path);
    }

    static InetAddress fromBytes(final byte[] bytes, final String path) {
        try {
            // Drops the host name and any IPv6 scope id; IPv4-mapped addresses become IPv4.
            return InetAddress.getByAddress(bytes);
        } catch (final UnknownHostException e) {
            throw error(path, Code.INVALID_FORMAT, "not an IPv4 or IPv6 address");
        }
    }

    static String identifier(final String value, final String path) {
        Objects.requireNonNull(value, path);
        final String lower = value.toLowerCase(Locale.ROOT);
        if (lower.length() > Lengths.MAX_IDENTIFIER || !IDENTIFIER.matcher(lower).matches()) {
            throw error(path, Code.INVALID_IDENTIFIER, "must be 1-" + Lengths.MAX_IDENTIFIER
                    + " characters of a-z, 0-9, '_' or '-': '" + value + "'");
        }
        return lower;
    }

    static String source(final String value, final String path) {
        Objects.requireNonNull(value, path);
        final String lower = value.toLowerCase(Locale.ROOT);
        if (lower.length() > Lengths.MAX_SOURCE || !SOURCE.matcher(lower).matches()) {
            throw error(path, Code.INVALID_FORMAT, "must be 1-" + Lengths.MAX_SOURCE
                    + " characters of a-z, 0-9, '_', '.', ':' or '-': '" + value + "'");
        }
        return lower;
    }

    static String text(final String value, final int maxLength, final String path) {
        Objects.requireNonNull(value, path);
        if (value.length() > maxLength) {
            throw error(path, Code.TOO_LONG, "is longer than " + maxLength + " characters");
        }
        return value;
    }

    static String name(final String value, final int maxLength, final String path) {
        text(value, maxLength, path);
        if (value.isBlank()) {
            throw error(path, Code.BLANK, "must not be blank");
        }
        return value;
    }
}
