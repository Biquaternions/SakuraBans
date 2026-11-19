package net.serlith.sakurabans.api.punish;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import net.serlith.sakurabans.api.model.Actor;
import net.serlith.sakurabans.api.model.Lengths;
import net.serlith.sakurabans.api.model.Visibility;
import net.serlith.sakurabans.api.validation.FieldError;
import net.serlith.sakurabans.api.validation.ValidationException;
import net.serlith.sakurabans.api.validation.Validator;
import org.jspecify.annotations.Nullable;

/**
 * A request to end punishments early (unban, unmute, unwarn). Build one with
 * {@link #builder(RevocationTarget, Actor)}.
 */
public interface RevocationRequest {

    /**
     * Which punishments to end.
     *
     * @return the target
     */
    RevocationTarget target();

    /**
     * Who ends them, and through which surface.
     *
     * @return the actor
     */
    Actor actor();

    /**
     * The reason, as plain text.
     *
     * @return the reason, or empty
     */
    Optional<String> reason();

    /**
     * Who is told about the revocation.
     *
     * @return the visibility; {@link Visibility#NORMAL} by default
     */
    Visibility visibility();

    /**
     * Whether only punishments issued by the actor's operator may be ended (the {@code .own}
     * permissions). Others fail with {@code NOT_OWN}.
     *
     * @return {@code false} by default
     */
    boolean ownOnly();

    /**
     * A builder that starts with this request's values.
     *
     * @return a new builder
     */
    default Builder toBuilder() {
        return builder(this.target(), this.actor())
                .reason(this.reason().orElse(null))
                .visibility(this.visibility())
                .ownOnly(this.ownOnly());
    }

    /**
     * Starts a request.
     *
     * @param target which punishments to end
     * @param actor who ends them
     * @return a new builder
     */
    static Builder builder(final RevocationTarget target, final Actor actor) {
        return new Builder(target, actor);
    }

    /**
     * Builds a {@link RevocationRequest}. Not thread-safe.
     */
    final class Builder {

        private final RevocationTarget target;
        private final Actor actor;
        private @Nullable String reason;
        private Visibility visibility = Visibility.NORMAL;
        private boolean ownOnly;

        private Builder(final RevocationTarget target, final Actor actor) {
            this.target = Objects.requireNonNull(target, "target");
            this.actor = Objects.requireNonNull(actor, "actor");
        }

        /**
         * Sets the reason.
         *
         * @param reason the reason, plain text, or {@code null} for none
         * @return this builder
         */
        public Builder reason(final @Nullable String reason) {
            this.reason = reason;
            return this;
        }

        /**
         * Sets the visibility.
         *
         * @param visibility who is told
         * @return this builder
         */
        public Builder visibility(final Visibility visibility) {
            this.visibility = Objects.requireNonNull(visibility, "visibility");
            return this;
        }

        /**
         * Sets whether only the operator's own punishments may be ended.
         *
         * @param ownOnly {@code true} to restrict to them
         * @return this builder
         */
        public Builder ownOnly(final boolean ownOnly) {
            this.ownOnly = ownOnly;
            return this;
        }

        /**
         * Checks the values without building.
         *
         * @return every problem: {@code reason} too long
         */
        public List<FieldError> validate() {
            final Validator errors = Validator.create();
            errors.maxLength("reason", this.reason, Lengths.MAX_REASON);
            return errors.errors();
        }

        /**
         * Builds the request.
         *
         * @return the request
         * @throws ValidationException with every problem {@link #validate()} reports
         */
        public RevocationRequest build() {
            record Impl(RevocationTarget target, Actor actor, Optional<String> reason,
                    Visibility visibility, boolean ownOnly) implements RevocationRequest {
            }

            ValidationException.throwIfAny(this.validate());
            return new Impl(this.target, this.actor, Optional.ofNullable(this.reason),
                    this.visibility, this.ownOnly);
        }
    }
}
