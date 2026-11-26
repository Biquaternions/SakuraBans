package net.serlith.sakurabans.api.template;

import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import net.serlith.sakurabans.api.model.PunishmentType;
import net.serlith.sakurabans.api.validation.FieldError.Code;
import net.serlith.sakurabans.api.validation.Validator;
import org.jspecify.annotations.Nullable;

/**
 * One step of a template's ladder.
 */
public interface TemplateStep {

    /** The most actions a step or a template may have. */
    int MAX_ACTIONS = 16;

    /** The longest action. */
    int MAX_ACTION_LENGTH = 255;

    /** The longest message, as for message overrides. */
    int MAX_MESSAGE_LENGTH = 4096;

    /**
     * The type of the punishment at this step, when the step names one ({@code kick},
     * {@code mute 1h}, {@code ban 7d}); a step without one uses the template's type (see
     * {@link TemplateInfo#type(int)}).
     *
     * @return the step's own type, or empty for the template's
     */
    Optional<PunishmentType> type();

    /**
     * How long a punishment at this step lasts.
     *
     * @return the duration, or empty for permanent (always empty for a kick)
     */
    Optional<Duration> duration();

    /**
     * What the player punished at this step is shown instead of the template's
     * {@link TemplateInfo#message()} and the network's message for the type: MiniMessage, with
     * the placeholders of the network's message.
     *
     * @return the message, or empty to use the template's
     */
    Optional<String> message();

    /**
     * Console commands run after a punishment at this step was created, after the template's own
     * actions. They may use the placeholders {@code {victim}}, {@code {uuid}}, {@code {address}},
     * {@code {id}}, {@code {operator}}, {@code {reason}}, {@code {template}}, {@code {step}}
     * (counted from 1) and {@code {duration}}.
     *
     * @return the commands, without a leading slash, possibly empty
     */
    List<String> actions();

    /**
     * Creates a step without actions.
     *
     * @param duration how long it lasts, positive, or {@code null} for permanent
     * @return the step
     * @throws net.serlith.sakurabans.api.validation.ValidationException if the duration isn't
     *         positive
     */
    static TemplateStep of(final @Nullable Duration duration) {
        return of(duration, List.of());
    }

    /**
     * Creates a step.
     *
     * @param duration how long it lasts, positive, or {@code null} for permanent
     * @param actions console commands to run, see {@link #actions()}; a leading slash is removed
     * @return the step
     * @throws net.serlith.sakurabans.api.validation.ValidationException if the duration isn't
     *         positive or an action is invalid (see {@link #validateActions})
     */
    static TemplateStep of(final @Nullable Duration duration, final List<String> actions) {
        return of(null, duration, actions, null);
    }

    /**
     * Creates a step that may name its own type and message.
     *
     * @param type the step's type, or {@code null} for the template's
     * @param duration how long it lasts, positive, or {@code null} for permanent; {@code null}
     *        for a kick
     * @param actions console commands to run, see {@link #actions()}
     * @param message what the punished player is shown, see {@link #message()}, or
     *        {@code null}
     * @return the step
     * @throws net.serlith.sakurabans.api.validation.ValidationException if the duration isn't
     *         positive, a kick has one, an action is invalid or the message is blank or longer
     *         than {@value #MAX_MESSAGE_LENGTH} characters
     */
    static TemplateStep of(final @Nullable PunishmentType type, final @Nullable Duration duration,
            final List<String> actions, final @Nullable String message) {
        record Impl(Optional<PunishmentType> type, Optional<Duration> duration,
                List<String> actions, Optional<String> message) implements TemplateStep {
        }

        final Validator errors = Validator.create()
                .check(duration == null || duration.isPositive(), "duration", Code.NOT_POSITIVE,
                        "must be positive")
                .check(duration == null || type == null || !type.instant(), "duration",
                        Code.NOT_ALLOWED, "a kick has no duration");
        validateMessage(errors, "message", message);
        validateActions(errors, "actions", actions);
        errors.throwIfAny();
        return new Impl(Optional.ofNullable(type), Optional.ofNullable(duration),
                normalizeActions(actions), Optional.ofNullable(message));
    }

    /**
     * Checks a message: not blank and at most {@value #MAX_MESSAGE_LENGTH} characters.
     *
     * @param errors where to record the problems
     * @param path the path of the message
     * @param message the message, or {@code null} for none
     * @return {@code errors}
     */
    static Validator validateMessage(final Validator errors, final String path,
            final @Nullable String message) {
        return errors.check(message == null || !message.isBlank(), path, Code.BLANK,
                        "must not be blank")
                .check(message == null || message.length() <= MAX_MESSAGE_LENGTH, path,
                        Code.TOO_LONG, "is longer than " + MAX_MESSAGE_LENGTH + " characters");
    }

    /**
     * Checks a list of actions: at most {@value #MAX_ACTIONS}, each one line, not blank and at
     * most {@value #MAX_ACTION_LENGTH} characters long.
     *
     * @param errors where to record the problems
     * @param path the path of the list
     * @param actions the actions
     * @return {@code errors}
     */
    static Validator validateActions(final Validator errors, final String path,
            final List<String> actions) {
        Objects.requireNonNull(actions, path);
        errors.check(actions.size() <= MAX_ACTIONS, path, Code.OUT_OF_RANGE,
                "has more than " + MAX_ACTIONS + " actions");
        for (int i = 0; i < actions.size(); i++) {
            final String action = Objects.requireNonNull(actions.get(i), path);
            final String command = strip(action);
            errors.check(!command.isEmpty(), path + "[" + i + "]", Code.BLANK,
                            "must not be blank")
                    .check(command.length() <= MAX_ACTION_LENGTH, path + "[" + i + "]",
                            Code.TOO_LONG, "is longer than " + MAX_ACTION_LENGTH + " characters")
                    .check(command.lines().count() <= 1, path + "[" + i + "]",
                            Code.INVALID_FORMAT, "must be one line");
        }
        return errors;
    }

    /**
     * Strips each action and removes a leading slash.
     *
     * @param actions valid actions
     * @return the normalized, immutable list
     */
    static List<String> normalizeActions(final List<String> actions) {
        return actions.stream().map(TemplateStep::strip).toList();
    }

    private static String strip(final String action) {
        final String stripped = action.strip();
        return stripped.startsWith("/") ? stripped.substring(1).strip() : stripped;
    }
}
