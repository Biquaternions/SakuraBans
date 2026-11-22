package net.serlith.sakurabans.core.command.parser;

import java.time.Duration;
import java.util.Locale;
import java.util.Optional;
import net.serlith.sakurabans.core.util.Durations;
import org.jspecify.annotations.Nullable;

public record PunishTail(@Nullable Duration duration, boolean permanent,
        @Nullable String template, @Nullable String reason) {

    public static final PunishTail EMPTY = new PunishTail(null, false, null, null);

    public static PunishTail parse(final @Nullable String text, final boolean durations) {
        if (text == null || text.isBlank()) {
            return EMPTY;
        }
        String rest = text.strip();
        Duration duration = null;
        boolean permanent = false;
        if (durations) {
            final String first = firstWord(rest);
            if (Durations.isPermanentToken(first)) {
                permanent = true;
                rest = afterFirstWord(rest);
            } else {
                final Optional<Duration> parsed = Durations.parse(first);
                if (parsed.isPresent()) {
                    duration = parsed.get();
                    rest = afterFirstWord(rest);
                }
            }
        }
        String template = null;
        final String next = firstWord(rest);
        if (next.length() > 1 && next.startsWith("#")) {
            template = next.substring(1).toLowerCase(Locale.ROOT);
            rest = afterFirstWord(rest);
        }
        return new PunishTail(duration, permanent, template, rest.isEmpty() ? null : rest);
    }

    private static String firstWord(final String text) {
        final int space = indexOfWhitespace(text);
        return space < 0 ? text : text.substring(0, space);
    }

    private static String afterFirstWord(final String text) {
        final int space = indexOfWhitespace(text);
        return space < 0 ? "" : text.substring(space).strip();
    }

    private static int indexOfWhitespace(final String text) {
        for (int i = 0; i < text.length(); i++) {
            if (Character.isWhitespace(text.charAt(i))) {
                return i;
            }
        }
        return -1;
    }
}
