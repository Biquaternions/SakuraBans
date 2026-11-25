package net.serlith.sakurabans.core.message;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.serlith.sakurabans.api.model.Punishment;
import net.serlith.sakurabans.core.configuration.ConfigService;

public final class Formats {

    private static final long MINUTE = 60;
    private static final long HOUR = 60 * MINUTE;
    private static final long DAY = 24 * HOUR;
    private static final long YEAR = 365 * DAY;

    private record Unit(long seconds, MessageKey one, MessageKey many) {
    }

    private static final List<Unit> UNITS = List.of(
            new Unit(YEAR, MessageKey.TIME_YEAR, MessageKey.TIME_YEARS),
            new Unit(DAY, MessageKey.TIME_DAY, MessageKey.TIME_DAYS),
            new Unit(HOUR, MessageKey.TIME_HOUR, MessageKey.TIME_HOURS),
            new Unit(MINUTE, MessageKey.TIME_MINUTE, MessageKey.TIME_MINUTES),
            new Unit(1, MessageKey.TIME_SECOND, MessageKey.TIME_SECONDS));

    private final MessageService messages;
    private final ConfigService config;

    public Formats(final MessageService messages, final ConfigService config) {
        this.messages = messages;
        this.config = config;
    }

    public String duration(final Duration duration, final Locale locale) {
        return this.units(duration, locale, UNITS.size());
    }

    public String approximate(final Duration duration, final Locale locale) {
        return this.units(duration, locale, 2);
    }

    private String units(final Duration duration, final Locale locale, final int precision) {
        long seconds = Math.max(0, duration.toSeconds());
        final List<String> parts = new ArrayList<>();
        int first = -1;
        for (int i = 0; i < UNITS.size(); i++) {
            final Unit unit = UNITS.get(i);
            final long count = seconds / unit.seconds();
            if (count > 0 && first < 0) {
                first = i;
            }
            if (first >= 0 && i >= first + precision) {
                break;
            }
            if (count > 0) {
                parts.add(this.messages.renderPlain(count == 1 ? unit.one() : unit.many(), locale,
                        Placeholder.unparsed("count", Long.toString(count))));
                seconds -= count * unit.seconds();
            }
        }
        if (parts.isEmpty()) {
            return this.messages.renderPlain(MessageKey.TIME_SECONDS, locale,
                    Placeholder.unparsed("count", "0"));
        }
        if (parts.size() == 1) {
            return parts.getFirst();
        }
        final String separator = this.messages.renderPlain(MessageKey.TIME_SEPARATOR, locale);
        final String last = this.messages.renderPlain(MessageKey.TIME_LAST_SEPARATOR, locale);
        return String.join(separator, parts.subList(0, parts.size() - 1)) + last + parts.getLast();
    }

    public String instant(final Instant instant, final Locale locale) {
        return this.config.settings().display().formatInstant(instant, locale);
    }

    public String length(final Punishment punishment, final Locale locale) {
        return punishment.expiresAt()
                .map(expiry -> Duration.between(punishment.createdAt(), expiry))
                .map(length -> this.duration(length, locale))
                .orElseGet(() -> this.messages.renderPlain(MessageKey.TIME_PERMANENT, locale));
    }

    public String remaining(final Punishment punishment, final Instant now, final Locale locale) {
        return punishment.expiresAt()
                .map(expiry -> this.approximate(Duration.between(now, expiry), locale))
                .orElseGet(() -> this.messages.renderPlain(MessageKey.TIME_PERMANENT, locale));
    }

    public String expiry(final Punishment punishment, final Locale locale) {
        return punishment.expiresAt()
                .map(expiry -> this.instant(expiry, locale))
                .orElseGet(() -> this.messages.renderPlain(MessageKey.TIME_NEVER, locale));
    }
}
