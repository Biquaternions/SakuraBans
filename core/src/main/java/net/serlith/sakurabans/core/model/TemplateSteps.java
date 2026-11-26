package net.serlith.sakurabans.core.model;

import java.time.Duration;
import java.util.Arrays;
import java.util.Locale;
import java.util.Optional;
import net.serlith.sakurabans.api.model.PunishmentType;
import net.serlith.sakurabans.api.template.TemplateStep;
import net.serlith.sakurabans.core.util.Durations;
import org.jspecify.annotations.Nullable;

/**
 * A ladder step as text (protocol 5.4): a duration, {@code permanent}, or a type followed by a
 * duration where the type has one ({@code kick}, {@code mute 1h}, {@code ban permanent}).
 */
public final class TemplateSteps {

    private static final String PERMANENT = "permanent";

    private TemplateSteps() {
    }

    /** The parts of a step's text, or why it isn't one. */
    public record Parsed(@Nullable PunishmentType type, @Nullable Duration duration,
            @Nullable String error) {
    }

    public static Parsed parse(final String text) {
        final String[] words = text.strip().split("\\s+");
        if (words.length == 1 && !words[0].isEmpty()) {
            final PunishmentType type = type(words[0]).orElse(null);
            if (type != null) {
                return type.instant() ? new Parsed(type, null, null) : new Parsed(null, null,
                        "\"" + text + "\" needs a duration or 'permanent' after the type");
            }
            return duration(null, words[0], text);
        }
        if (words.length == 2) {
            final PunishmentType type = type(words[0]).orElse(null);
            if (type == null) {
                return new Parsed(null, null, "\"" + words[0]
                        + "\" is not a type (ban, mute, warn or kick)");
            }
            if (type.instant()) {
                return new Parsed(null, null, "a kick has no duration");
            }
            return duration(type, words[1], text);
        }
        return new Parsed(null, null, "\"" + text + "\" is not a step (a duration, 'permanent',"
                + " or a type and a duration)");
    }

    private static Parsed duration(final @Nullable PunishmentType type, final String word,
            final String text) {
        if (Durations.isPermanentToken(word)) {
            return new Parsed(type, null, null);
        }
        final Duration duration = Durations.parse(word).orElse(null);
        return duration == null
                ? new Parsed(null, null, "\"" + text + "\" is not a duration or 'permanent'")
                : new Parsed(type, duration, null);
    }

    /** The step as text; its type only when it differs from the template's. */
    public static String format(final TemplateStep step, final PunishmentType templateType) {
        final PunishmentType type = step.type().filter(own -> own != templateType).orElse(null);
        final String duration = step.duration()
                .map(value -> Durations.formatCompact(value).replace(" ", ""))
                .orElse(PERMANENT);
        if (type == null) {
            return duration;
        }
        return type.instant() ? lower(type) : lower(type) + " " + duration;
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
