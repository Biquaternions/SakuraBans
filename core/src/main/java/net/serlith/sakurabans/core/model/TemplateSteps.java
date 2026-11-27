package net.serlith.sakurabans.core.model;

import java.time.Duration;
import java.util.Arrays;
import java.util.Locale;
import java.util.Optional;
import net.serlith.sakurabans.api.model.PunishmentType;
import net.serlith.sakurabans.api.template.TemplateDuration;
import net.serlith.sakurabans.api.template.TemplateStep;
import net.serlith.sakurabans.core.util.Durations;
import org.jspecify.annotations.Nullable;

/**
 * A ladder step as text (protocol 5.4): {@code none}, {@code kick}, {@code warn}, or a type and a
 * duration ({@code warn 30d}, {@code mute 1h}, {@code ban permanent}); and a preset's duration.
 */
public final class TemplateSteps {

    public static final String NONE = "none";
    private static final String PERMANENT = "permanent";

    private TemplateSteps() {
    }

    /**
     * The parts of a step's text, or why it isn't one. {@code type} null: no punishment.
     */
    public record Parsed(@Nullable PunishmentType type, TemplateDuration duration,
            @Nullable String error) {

        static Parsed error(final String error) {
            return new Parsed(null, TemplateDuration.open(), error);
        }
    }

    public static Parsed parse(final String text) {
        final String[] words = text.strip().split("\\s+");
        if (words.length == 0 || words.length > 2 || words[0].isEmpty()) {
            return Parsed.error("\"" + text + "\" is not a step (none, kick, warn, or a type"
                    + " and a duration: ban 7d, mute 1h, ban permanent)");
        }
        final boolean none = words[0].equalsIgnoreCase(NONE);
        final PunishmentType type = type(words[0]).orElse(null);
        if (!none && type == null) {
            return Parsed.error(Durations.isPermanentToken(words[0])
                    || Durations.parse(words[0]).isPresent()
                    ? "\"" + text + "\" names no type: a step is none, kick, warn, or a type and"
                            + " a duration (ban 7d, mute 1h, ban permanent)"
                    : "\"" + words[0] + "\" is not a type (none, ban, mute, warn or kick)");
        }
        if (words.length == 1) {
            if (none || type.instant() || type == PunishmentType.WARN) {
                return new Parsed(type, TemplateDuration.open(), null);
            }
            return Parsed.error("\"" + text + "\" needs a duration or 'permanent' after the type");
        }
        if (none || type.instant()) {
            return Parsed.error(none ? "a step without a punishment has no duration"
                    : "a kick has no duration");
        }
        final TemplateDuration duration = duration(words[1]).orElse(null);
        return duration == null
                ? Parsed.error(durationError(words[1]))
                : new Parsed(type, duration, null);
    }

    /**
     * {@code permanent} or a duration of whole seconds, at least one; empty when it is neither
     * ({@link #durationError} says why).
     */
    public static Optional<TemplateDuration> duration(final String text) {
        if (Durations.isPermanentToken(text.strip())) {
            return Optional.of(TemplateDuration.permanent());
        }
        return Durations.parse(text.strip()).filter(TemplateSteps::wholeSeconds)
                .map(TemplateDuration::fixed);
    }

    /** Why {@link #duration} refused the text. */
    public static String durationError(final String text) {
        final Duration length = Durations.parse(text.strip()).orElse(null);
        if (length == null) {
            return "\"" + text + "\" is not a duration or 'permanent'";
        }
        return "\"" + text + "\" " + (length.compareTo(Duration.ofSeconds(1)) < 0
                ? "is under one second" : "is not whole seconds")
                + ": a template's durations are whole seconds";
    }

    public static boolean wholeSeconds(final Duration length) {
        return length.compareTo(Duration.ofSeconds(1)) >= 0 && length.getNano() == 0;
    }

    /** The duration as text; empty when open. */
    public static Optional<String> format(final TemplateDuration duration) {
        return switch (duration) {
            case TemplateDuration.Open open -> Optional.empty();
            case TemplateDuration.Permanent permanent -> Optional.of(PERMANENT);
            case TemplateDuration.Fixed fixed -> Optional.of(compact(fixed.length()));
        };
    }

    public static String format(final TemplateStep step) {
        final PunishmentType type = step.type().orElse(null);
        if (type == null) {
            return NONE;
        }
        return format(step.duration()).map(text -> lower(type) + " " + text)
                .orElse(lower(type));
    }

    public static String compact(final Duration duration) {
        return Durations.formatCompact(duration).replace(" ", "");
    }

    private static Optional<PunishmentType> type(final String word) {
        return Arrays.stream(PunishmentType.values())
                .filter(value -> value.name().equalsIgnoreCase(word))
                .findFirst();
    }

    private static String lower(final PunishmentType type) {
        return type.name().toLowerCase(Locale.ROOT);
    }
}
