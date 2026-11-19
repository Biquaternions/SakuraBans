package net.serlith.sakurabans.api.punish;

import java.util.Locale;
import java.util.Objects;
import java.util.regex.Pattern;
import net.serlith.sakurabans.api.model.Lengths;

final class Validation {

    private static final Pattern IDENTIFIER = Pattern.compile("[a-z0-9_-]+");

    private Validation() {
    }

    static String reason(final String reason) {
        Objects.requireNonNull(reason, "reason");
        if (reason.length() > Lengths.MAX_REASON) {
            throw new IllegalArgumentException(
                    "reason is longer than " + Lengths.MAX_REASON + " characters");
        }
        return reason;
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
}
