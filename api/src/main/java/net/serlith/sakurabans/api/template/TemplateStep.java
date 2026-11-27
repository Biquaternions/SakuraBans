package net.serlith.sakurabans.api.template;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import net.serlith.sakurabans.api.model.Lengths;
import net.serlith.sakurabans.api.model.PunishmentType;
import net.serlith.sakurabans.api.validation.FieldError.Code;
import net.serlith.sakurabans.api.validation.Validator;
import org.jspecify.annotations.Nullable;

/**
 * One step of a {@link LadderTemplate}: a punishment of its own type, or none (only actions).
 */
public interface TemplateStep {

    /** The most actions a step or a template may have. */
    int MAX_ACTIONS = 16;

    /** The longest action. */
    int MAX_ACTION_LENGTH = 255;

    /** The longest message, as for message overrides. */
    int MAX_MESSAGE_LENGTH = 4096;

    /**
     * The type of the punishment at this step ({@code warn}, {@code kick}, {@code mute 1h},
     * {@code ban 7d}).
     *
     * @return the type, or empty when the step punishes nothing and only runs its actions
     */
    Optional<PunishmentType> type();

    /**
     * How long the punishment at this step lasts. Open means the type's default (a warning's
     * {@code warnings.expire-after}); a kick and a step without a punishment are always open, a
     * ban or a mute never.
     *
     * @return the duration
     */
    TemplateDuration duration();

    /**
     * The reason of the punishment at this step, instead of the template's
     * {@link TemplateInfo#reason()}. A reason given with the command wins over both.
     *
     * @return the reason, plain text, or empty for the template's
     */
    Optional<String> reason();

    /**
     * What the player punished at this step is shown instead of the template's
     * {@link TemplateInfo#message()} and the network's message for the type: MiniMessage, with
     * the placeholders of the network's message.
     *
     * @return the message, or empty to use the template's; always empty without a punishment
     */
    Optional<String> message();

    /**
     * Console commands run when this step is applied, after the template's own actions, with
     * the same placeholders (see {@link TemplateInfo#actions()}). A step without a punishment
     * has at least one; there {@code {id}} and {@code {duration}} are empty.
     *
     * @return the commands, without a leading slash, possibly empty
     */
    List<String> actions();

    /**
     * Creates a step.
     *
     * @param type the punishment's type, or {@code null} for no punishment
     * @param duration how long it lasts, see {@link #duration()}
     * @param reason the step's reason, or {@code null} for the template's
     * @param message what the punished player is shown, see {@link #message()}, or
     *        {@code null}
     * @param actions console commands to run, see {@link #actions()}; a leading slash is removed
     * @return the step
     * @throws net.serlith.sakurabans.api.validation.ValidationException if the duration doesn't
     *         suit the type, the reason is blank or too long, an action is invalid (see
     *         {@link #validateActions}), the message is blank or longer than
     *         {@value #MAX_MESSAGE_LENGTH} characters, or a step without a punishment has a
     *         message or no action
     */
    static TemplateStep of(final @Nullable PunishmentType type, final TemplateDuration duration,
            final @Nullable String reason, final @Nullable String message,
            final List<String> actions) {
        record Impl(Optional<PunishmentType> type, TemplateDuration duration,
                Optional<String> reason, Optional<String> message, List<String> actions)
                implements TemplateStep {
        }

        Objects.requireNonNull(duration, "duration");
        Objects.requireNonNull(actions, "actions");
        final boolean open = duration instanceof TemplateDuration.Open;
        final Validator errors = Validator.create()
                .check(open || type != null && !type.instant(), "duration", Code.NOT_ALLOWED,
                        type == null ? "a step without a punishment has no duration"
                                : "a kick has no duration")
                .check(!open || type == null || type.instant() || type == PunishmentType.WARN,
                        "duration", Code.NOT_ALLOWED,
                        "a ban or mute step needs a duration or permanent")
                .check(type != null || message == null, "message", Code.NOT_ALLOWED,
                        "a step without a punishment has no message")
                .check(type != null || !actions.isEmpty(), "actions", Code.NOT_ALLOWED,
                        "a step without a punishment needs an action")
                .name("reason", reason, Lengths.MAX_REASON);
        validateMessage(errors, "message", message);
        validateActions(errors, "actions", actions);
        errors.throwIfAny();
        return new Impl(Optional.ofNullable(type), duration,
                Optional.ofNullable(reason).map(String::strip), Optional.ofNullable(message),
                normalizeActions(actions));
    }

    /**
     * Creates a step of a type, with the template's reason and message and no actions.
     *
     * @param type the punishment's type
     * @param duration how long it lasts, see {@link #duration()}
     * @return the step
     * @throws net.serlith.sakurabans.api.validation.ValidationException if the duration doesn't
     *         suit the type
     */
    static TemplateStep of(final PunishmentType type, final TemplateDuration duration) {
        return of(Objects.requireNonNull(type, "type"), duration, null, null, List.of());
    }

    /**
     * Creates a step without a punishment.
     *
     * @param actions console commands to run, at least one
     * @return the step
     * @throws net.serlith.sakurabans.api.validation.ValidationException if there is no action or
     *         one is invalid
     */
    static TemplateStep none(final List<String> actions) {
        return of(null, TemplateDuration.open(), null, null, actions);
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
