package net.serlith.sakurabans.core.util;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.jspecify.annotations.Nullable;

public final class Durations {

    private static final long MINUTE = 60;
    private static final long HOUR = 60 * MINUTE;
    private static final long DAY = 24 * HOUR;
    private static final long YEAR = 365 * DAY;

    private static final Map<String, Long> UNITS = Map.ofEntries(
            Map.entry("s", 1L), Map.entry("sec", 1L), Map.entry("secs", 1L),
            Map.entry("second", 1L), Map.entry("seconds", 1L),
            Map.entry("m", MINUTE), Map.entry("min", MINUTE), Map.entry("mins", MINUTE),
            Map.entry("minute", MINUTE), Map.entry("minutes", MINUTE),
            Map.entry("h", HOUR), Map.entry("hr", HOUR), Map.entry("hrs", HOUR),
            Map.entry("hour", HOUR), Map.entry("hours", HOUR),
            Map.entry("d", DAY), Map.entry("day", DAY), Map.entry("days", DAY),
            Map.entry("w", 7 * DAY), Map.entry("wk", 7 * DAY), Map.entry("week", 7 * DAY),
            Map.entry("weeks", 7 * DAY),
            Map.entry("mo", 30 * DAY), Map.entry("mon", 30 * DAY), Map.entry("month", 30 * DAY),
            Map.entry("months", 30 * DAY),
            Map.entry("y", YEAR), Map.entry("yr", YEAR), Map.entry("year", YEAR),
            Map.entry("years", YEAR));

    private static final Pattern PART = Pattern.compile("(\\d{1,12}(?:\\.\\d{1,6})?)([a-z]+)");
    private static final Pattern ZERO = Pattern.compile("0+(?:\\.0+)?([a-z]*)");
    private static final Set<String> PERMANENT = Set.of("perm", "permanent", "forever");
    private static final BigDecimal MILLISECOND = new BigDecimal("0.001");
    private static final BigDecimal MAX_SECONDS = BigDecimal.valueOf(Long.MAX_VALUE / 1000);
    private static final List<Map.Entry<String, Long>> COMPACT_PARTS = List.of(
            Map.entry("y", YEAR), Map.entry("d", DAY), Map.entry("h", HOUR),
            Map.entry("m", MINUTE), Map.entry("s", 1L));

    private Durations() {
    }

    /**
     * Parses {@code 90m}, {@code 1.5h}, {@code 1d12h}, {@code 2w}, {@code 1mo}, {@code 1y},
     * {@code 500ms} and the long unit names, in any case. Millisecond precision; zero, unknown
     * units, a missing unit and values too large for a millisecond count are rejected.
     */
    public static Optional<Duration> parse(final String text) {
        final String input = text.strip().toLowerCase(Locale.ROOT);
        if (input.isEmpty()) {
            return Optional.empty();
        }
        final Matcher matcher = PART.matcher(input);
        BigDecimal seconds = BigDecimal.ZERO;
        int end = 0;
        while (matcher.lookingAt()) {
            final BigDecimal unit = unit(matcher.group(2));
            if (unit == null) {
                return Optional.empty();
            }
            final BigDecimal amount = new BigDecimal(matcher.group(1));
            seconds = seconds.add(amount.multiply(unit));
            if (seconds.compareTo(MAX_SECONDS) > 0) {
                return Optional.empty();
            }
            end = matcher.end();
            matcher.region(end, input.length());
        }
        if (end != input.length()) {
            return Optional.empty();
        }
        final long millis = seconds.movePointRight(3).setScale(0, RoundingMode.HALF_UP).longValue();
        return millis > 0 ? Optional.of(Duration.ofMillis(millis)) : Optional.empty();
    }

    /** Whether the text is zero ({@code 0}, {@code 0s}), which {@link #parse} rejects. */
    public static boolean isZero(final String text) {
        final Matcher matcher = ZERO.matcher(text.strip().toLowerCase(Locale.ROOT));
        return matcher.matches() && (matcher.group(1).isEmpty()
                || unit(matcher.group(1)) != null);
    }

    // Milliseconds only for settings like sync.poll-interval (250ms); "ms" isn't "m" + "s".
    private static @Nullable BigDecimal unit(final String name) {
        if (name.equals("ms")) {
            return MILLISECOND;
        }
        final Long seconds = UNITS.get(name);
        return seconds == null ? null : BigDecimal.valueOf(seconds);
    }

    public static boolean isPermanentToken(final String text) {
        return PERMANENT.contains(text.strip().toLowerCase(Locale.ROOT));
    }

    /**
     * Formats as {@code 1y 2d 3h 4m 5s}, leaving out zero parts and anything below a second.
     * Without the spaces, {@link #parse(String)} reads it back (to the second); the spaces stay
     * because a command's duration is one token and parse rejects them.
     */
    public static String formatCompact(final Duration duration) {
        long seconds = Math.max(0, duration.toSeconds());
        if (seconds == 0) {
            return "0s";
        }
        final StringBuilder text = new StringBuilder();
        for (final Map.Entry<String, Long> part : COMPACT_PARTS) {
            final long count = seconds / part.getValue();
            if (count > 0) {
                if (!text.isEmpty()) {
                    text.append(' ');
                }
                text.append(count).append(part.getKey());
                seconds -= count * part.getValue();
            }
        }
        return text.toString();
    }
}
