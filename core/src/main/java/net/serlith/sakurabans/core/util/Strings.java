package net.serlith.sakurabans.core.util;

import java.util.Locale;
import java.util.regex.Pattern;
import net.serlith.sakurabans.api.model.Lengths;

public final class Strings {

    // Mojang allows 1-16 characters of letters, digits and '_' (older accounts may be shorter
    // than the 3 characters new names need).
    private static final Pattern JAVA_NAME = Pattern.compile("[A-Za-z0-9_]{1,16}");
    private static final Pattern BEDROCK_NAME = Pattern.compile("[A-Za-z0-9_]{1,31}");

    private Strings() {
    }

    public static String lower(final String text) {
        return text.toLowerCase(Locale.ROOT);
    }

    public static String truncate(final String text, final int maxLength) {
        if (maxLength < 0) {
            throw new IllegalArgumentException("maxLength must not be negative: " + maxLength);
        }
        if (text.length() <= maxLength) {
            return text;
        }
        final int end = maxLength > 0 && Character.isHighSurrogate(text.charAt(maxLength - 1))
                ? maxLength - 1
                : maxLength;
        return text.substring(0, end);
    }

    public static boolean isValidPlayerName(final String name) {
        return JAVA_NAME.matcher(name).matches();
    }

    public static boolean isValidPlayerName(final String name, final String floodgatePrefix) {
        if (isValidPlayerName(name)) {
            return true;
        }
        return !floodgatePrefix.isEmpty()
                && name.startsWith(floodgatePrefix)
                && name.length() <= Lengths.MAX_PLAYER_NAME
                && BEDROCK_NAME.matcher(name.substring(floodgatePrefix.length())).matches();
    }

}
