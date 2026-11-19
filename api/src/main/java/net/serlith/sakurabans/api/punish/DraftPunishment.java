package net.serlith.sakurabans.api.punish;

import java.time.Duration;
import java.util.Objects;
import java.util.Optional;
import net.serlith.sakurabans.api.model.Operator;
import net.serlith.sakurabans.api.model.PunishmentType;
import net.serlith.sakurabans.api.model.Scope;
import net.serlith.sakurabans.api.model.Victim;
import org.jspecify.annotations.Nullable;

/**
 * A punishment to be created. Build one with {@link #builder(PunishmentType, Victim, Operator)}
 * and pass it to {@link PunishmentService#punish(DraftPunishment, PunishOptions)}.
 *
 * <p>Values left empty are filled in by SakuraBans from its configuration: the default scope, the
 * template's or the default reason, and the template's duration.
 */
public interface DraftPunishment {

    /**
     * The kind of punishment.
     *
     * @return the type
     */
    PunishmentType type();

    /**
     * Who it applies to.
     *
     * @return the victim
     */
    Victim victim();

    /**
     * Who issues it.
     *
     * @return the operator
     */
    Operator operator();

    /**
     * Where it applies.
     *
     * @return the scope, or empty for the configured default scope
     */
    Optional<Scope> scope();

    /**
     * The reason, as plain text.
     *
     * @return the reason, or empty for the template's or the configured default reason
     */
    Optional<String> reason();

    /**
     * How long it lasts.
     *
     * @return the duration, or empty for permanent (or the template's duration if a template is
     *         set)
     */
    Optional<Duration> duration();

    /**
     * The template to apply. The template decides the duration from the victim's ladder step.
     *
     * @return the template name, lower case, or empty
     */
    Optional<String> template();

    /**
     * A builder that starts with this draft's values.
     *
     * @return a new builder
     */
    default Builder toBuilder() {
        final Builder builder = builder(this.type(), this.victim(), this.operator());
        this.scope().ifPresent(builder::scope);
        this.reason().ifPresent(builder::reason);
        this.duration().ifPresent(builder::duration);
        this.template().ifPresent(builder::template);
        return builder;
    }

    /**
     * Starts a draft.
     *
     * @param type the kind of punishment
     * @param victim who it applies to
     * @param operator who issues it
     * @return a new builder
     */
    static Builder builder(
            final PunishmentType type, final Victim victim, final Operator operator) {
        return new Builder(type, victim, operator);
    }

    /**
     * Builds a {@link DraftPunishment}. Not thread-safe.
     */
    final class Builder {

        private PunishmentType type;
        private Victim victim;
        private Operator operator;
        private @Nullable Scope scope;
        private @Nullable String reason;
        private @Nullable Duration duration;
        private @Nullable String template;

        private Builder(final PunishmentType type, final Victim victim, final Operator operator) {
            this.type = Objects.requireNonNull(type, "type");
            this.victim = Objects.requireNonNull(victim, "victim");
            this.operator = Objects.requireNonNull(operator, "operator");
        }

        /**
         * Sets the kind of punishment.
         *
         * @param type the type
         * @return this builder
         */
        public Builder type(final PunishmentType type) {
            this.type = Objects.requireNonNull(type, "type");
            return this;
        }

        /**
         * Sets the victim.
         *
         * @param victim who it applies to
         * @return this builder
         */
        public Builder victim(final Victim victim) {
            this.victim = Objects.requireNonNull(victim, "victim");
            return this;
        }

        /**
         * Sets the operator.
         *
         * @param operator who issues it
         * @return this builder
         */
        public Builder operator(final Operator operator) {
            this.operator = Objects.requireNonNull(operator, "operator");
            return this;
        }

        /**
         * Sets the scope.
         *
         * @param scope where it applies, or {@code null} for the configured default
         * @return this builder
         */
        public Builder scope(final @Nullable Scope scope) {
            this.scope = scope;
            return this;
        }

        /**
         * Sets the reason.
         *
         * @param reason the reason, plain text, or {@code null} for the default reason
         * @return this builder
         */
        public Builder reason(final @Nullable String reason) {
            this.reason = reason;
            return this;
        }

        /**
         * Sets the duration.
         *
         * @param duration how long it lasts, positive, or {@code null} for permanent
         * @return this builder
         */
        public Builder duration(final @Nullable Duration duration) {
            this.duration = duration;
            return this;
        }

        /**
         * Makes it permanent; the same as {@code duration(null)}.
         *
         * @return this builder
         */
        public Builder permanent() {
            return this.duration(null);
        }

        /**
         * Sets the template.
         *
         * @param template the template name, or {@code null} for none
         * @return this builder
         */
        public Builder template(final @Nullable String template) {
            this.template = template;
            return this;
        }

        /**
         * Builds the draft.
         *
         * @return the draft
         * @throws IllegalArgumentException if the reason is too long, the duration isn't
         *         positive, a kick has a duration, the template name is invalid, or both a
         *         template and a duration are set
         */
        public DraftPunishment build() {
            record Impl(PunishmentType type, Victim victim, Operator operator,
                    Optional<Scope> scope, Optional<String> reason, Optional<Duration> duration,
                    Optional<String> template) implements DraftPunishment {
                Impl {
                    reason.ifPresent(Validation::reason);
                    template = template.map(name -> Validation.identifier(name, "template"));
                    if (duration.isPresent()) {
                        if (duration.get().isNegative() || duration.get().isZero()) {
                            throw new IllegalArgumentException("duration must be positive");
                        }
                        if (type.instant()) {
                            throw new IllegalArgumentException(type + " can't have a duration");
                        }
                        if (template.isPresent()) {
                            throw new IllegalArgumentException(
                                    "a template decides the duration; don't set both");
                        }
                    }
                }
            }

            return new Impl(this.type, this.victim, this.operator, Optional.ofNullable(this.scope),
                    Optional.ofNullable(this.reason), Optional.ofNullable(this.duration),
                    Optional.ofNullable(this.template));
        }
    }
}
