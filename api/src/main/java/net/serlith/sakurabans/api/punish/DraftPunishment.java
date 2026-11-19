package net.serlith.sakurabans.api.punish;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import net.serlith.sakurabans.api.model.Actor;
import net.serlith.sakurabans.api.model.EvidenceRef;
import net.serlith.sakurabans.api.model.Lengths;
import net.serlith.sakurabans.api.model.PunishmentType;
import net.serlith.sakurabans.api.model.Scope;
import net.serlith.sakurabans.api.model.Victim;
import net.serlith.sakurabans.api.validation.FieldError;
import net.serlith.sakurabans.api.validation.FieldError.Code;
import net.serlith.sakurabans.api.validation.ValidationException;
import net.serlith.sakurabans.api.validation.Validator;
import org.jspecify.annotations.Nullable;

/**
 * A punishment to be created. Build one with {@link #builder(PunishmentType, Victim, Actor)}
 * and pass it to {@link PunishmentService#punish(DraftPunishment, PunishOptions)}.
 *
 * <p>Values left empty are filled in by SakuraBans from its configuration: the kind named after
 * the type, the default scope, the template's or the default reason, and the template's duration.
 */
public interface DraftPunishment {

    /**
     * The kind of punishment, which decides how it is enforced.
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
     * Who issues it, and through which surface.
     *
     * @return the actor
     */
    Actor actor();

    /**
     * The punishment kind ({@code shadow-mute}, {@code blacklist}, ...). Its base type must be
     * {@link #type()}; it decides the modifiers (shadow, strictness) and may set a default scope.
     *
     * @return the kind id, lower case, or empty for the kind named after the type
     */
    Optional<String> kind();

    /**
     * Where it applies.
     *
     * @return the scope, or empty for the kind's or the configured default scope
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
     * Evidence to attach when it is created.
     *
     * @return the evidence references, without duplicates
     */
    List<EvidenceRef> evidence();

    /**
     * A builder that starts with this draft's values.
     *
     * @return a new builder
     */
    default Builder toBuilder() {
        final Builder builder = builder(this.type(), this.victim(), this.actor());
        this.kind().ifPresent(builder::kind);
        this.scope().ifPresent(builder::scope);
        this.reason().ifPresent(builder::reason);
        this.duration().ifPresent(builder::duration);
        this.template().ifPresent(builder::template);
        return builder.evidence(this.evidence());
    }

    /**
     * Starts a draft.
     *
     * @param type the kind of punishment
     * @param victim who it applies to
     * @param actor who issues it
     * @return a new builder
     */
    static Builder builder(final PunishmentType type, final Victim victim, final Actor actor) {
        return new Builder(type, victim, actor);
    }

    /**
     * Builds a {@link DraftPunishment}. Not thread-safe.
     */
    final class Builder {

        private PunishmentType type;
        private Victim victim;
        private Actor actor;
        private @Nullable String kind;
        private @Nullable Scope scope;
        private @Nullable String reason;
        private @Nullable Duration duration;
        private @Nullable String template;
        private List<EvidenceRef> evidence = List.of();

        private Builder(final PunishmentType type, final Victim victim, final Actor actor) {
            this.type = Objects.requireNonNull(type, "type");
            this.victim = Objects.requireNonNull(victim, "victim");
            this.actor = Objects.requireNonNull(actor, "actor");
        }

        /**
         * Sets the type.
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
         * Sets the actor.
         *
         * @param actor who issues it
         * @return this builder
         */
        public Builder actor(final Actor actor) {
            this.actor = Objects.requireNonNull(actor, "actor");
            return this;
        }

        /**
         * Sets the punishment kind.
         *
         * @param kind the kind id, or {@code null} for the kind named after the type
         * @return this builder
         */
        public Builder kind(final @Nullable String kind) {
            this.kind = kind;
            return this;
        }

        /**
         * Sets the scope.
         *
         * @param scope where it applies, or {@code null} for the default
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
         * Sets the evidence to attach. Duplicates (same URI) are dropped.
         *
         * @param evidence the evidence references
         * @return this builder
         */
        public Builder evidence(final List<EvidenceRef> evidence) {
            this.evidence = List.copyOf(evidence);
            return this;
        }

        /**
         * Checks the values without building.
         *
         * @return every problem: {@code reason} too long, {@code kind} or {@code template} not an
         *         identifier, {@code duration} not positive, not allowed on a kick, or exclusive
         *         with a template
         */
        public List<FieldError> validate() {
            final Validator errors = Validator.create();
            errors.maxLength("reason", this.reason, Lengths.MAX_REASON);
            errors.identifier("kind", this.kind);
            errors.identifier("template", this.template);
            if (this.duration != null) {
                errors.check(this.duration.isPositive(), "duration", Code.NOT_POSITIVE,
                        "must be positive");
                errors.check(!this.type.instant(), "duration", Code.NOT_ALLOWED,
                        this.type + " can't have a duration");
                errors.check(this.template == null, "duration", Code.EXCLUSIVE,
                        "a template decides the duration; don't set both");
            }
            return errors.errors();
        }

        /**
         * Builds the draft.
         *
         * @return the draft
         * @throws ValidationException with every problem
         *         {@link #validate()} reports
         */
        public DraftPunishment build() {
            record Impl(PunishmentType type, Victim victim, Actor actor, Optional<String> kind,
                    Optional<Scope> scope, Optional<String> reason, Optional<Duration> duration,
                    Optional<String> template, List<EvidenceRef> evidence)
                    implements DraftPunishment {
            }

            ValidationException.throwIfAny(this.validate());
            final Map<String, EvidenceRef> unique = new LinkedHashMap<>();
            this.evidence.forEach(ref -> unique.putIfAbsent(ref.uri(), ref));
            return new Impl(this.type, this.victim, this.actor,
                    Optional.ofNullable(this.kind).flatMap(Validator::normalizeIdentifier),
                    Optional.ofNullable(this.scope), Optional.ofNullable(this.reason),
                    Optional.ofNullable(this.duration),
                    Optional.ofNullable(this.template).flatMap(Validator::normalizeIdentifier),
                    List.copyOf(new ArrayList<>(unique.values())));
        }
    }
}
