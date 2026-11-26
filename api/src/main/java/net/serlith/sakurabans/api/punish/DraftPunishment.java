package net.serlith.sakurabans.api.punish;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import net.serlith.sakurabans.api.model.Actor;
import net.serlith.sakurabans.api.model.ErasedAddressVictim;
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
 * <p>Values left empty are filled in by SakuraBans from its configuration or the template: the
 * template's or the default scope, the template's or the default reason, the template's duration
 * and shadow flag.
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
     * Whether it is a shadow mute: the muted player's messages are shown to themselves only.
     * A template that sets {@code shadow} makes it a shadow mute as well.
     *
     * @return {@code true} for a shadow mute; only a mute can be one
     */
    boolean shadow();

    /**
     * Where it applies.
     *
     * @return the scope, or empty for the template's or the configured default scope
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
        builder.shadow(this.shadow());
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
        private boolean shadow;
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
         * Sets whether it is a shadow mute.
         *
         * @param shadow {@code true} for a shadow mute (mutes only)
         * @return this builder
         */
        public Builder shadow(final boolean shadow) {
            this.shadow = shadow;
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
         * @return every problem: {@code victim} an {@link ErasedAddressVictim}, {@code reason}
         *         too long, {@code shadow} on a type other than a mute, {@code template} not an
         *         identifier, {@code duration} not positive, not allowed on a kick, or exclusive
         *         with a template
         */
        public List<FieldError> validate() {
            final Validator errors = Validator.create();
            errors.check(!(this.victim instanceof ErasedAddressVictim), "victim",
                    Code.NOT_ALLOWED,
                    "an erased victim has no address; name the address or player instead");
            errors.maxLength("reason", this.reason, Lengths.MAX_REASON);
            errors.check(!this.shadow || this.type == PunishmentType.MUTE, "shadow",
                    Code.NOT_ALLOWED, "only mutes can be shadow mutes");
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
            record Impl(PunishmentType type, Victim victim, Actor actor, boolean shadow,
                    Optional<Scope> scope,
                    Optional<String> reason, Optional<Duration> duration,
                    Optional<String> template, List<EvidenceRef> evidence)
                    implements DraftPunishment {
            }

            ValidationException.throwIfAny(this.validate());
            final Map<String, EvidenceRef> unique = new LinkedHashMap<>();
            this.evidence.forEach(ref -> unique.putIfAbsent(ref.uri(), ref));
            return new Impl(this.type, this.victim, this.actor,
                    this.shadow,
                    Optional.ofNullable(this.scope), Optional.ofNullable(this.reason),
                    Optional.ofNullable(this.duration),
                    Optional.ofNullable(this.template).flatMap(Validator::normalizeIdentifier),
                    List.copyOf(new ArrayList<>(unique.values())));
        }
    }
}
