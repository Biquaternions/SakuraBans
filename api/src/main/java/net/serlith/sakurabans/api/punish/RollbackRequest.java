package net.serlith.sakurabans.api.punish;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import net.serlith.sakurabans.api.model.Actor;
import net.serlith.sakurabans.api.model.Lengths;
import net.serlith.sakurabans.api.model.Operator;
import net.serlith.sakurabans.api.model.Visibility;
import net.serlith.sakurabans.api.validation.FieldError;
import net.serlith.sakurabans.api.validation.ValidationException;
import net.serlith.sakurabans.api.validation.Validator;
import org.jspecify.annotations.Nullable;

/**
 * A request to undo a staff member's recent work ({@code /staffrollback}): every punishment the
 * operator issued since an instant and that is still active ends as {@code ROLLED_BACK}. Build
 * one with {@link #builder(Operator, Instant, Actor)}.
 */
public interface RollbackRequest {

    /**
     * Whose punishments to roll back. Matched by identity: a player or custom operator by UUID,
     * the console as the console.
     *
     * @return the operator
     */
    Operator target();

    /**
     * Only punishments issued at or after this instant are rolled back.
     *
     * @return the lower bound
     */
    Instant since();

    /**
     * Who performs the rollback, and through which surface.
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
     * Who is told about the rollback.
     *
     * @return the visibility; {@link Visibility#NORMAL} by default
     */
    Visibility visibility();

    /**
     * A builder that starts with this request's values.
     *
     * @return a new builder
     */
    default Builder toBuilder() {
        return builder(this.target(), this.since(), this.actor())
                .reason(this.reason().orElse(null))
                .visibility(this.visibility());
    }

    /**
     * Starts a request.
     *
     * @param target whose punishments to roll back
     * @param since the lower bound of their creation
     * @param actor who performs the rollback
     * @return a new builder
     */
    static Builder builder(final Operator target, final Instant since, final Actor actor) {
        return new Builder(target, since, actor);
    }

    /**
     * Builds a {@link RollbackRequest}. Not thread-safe.
     */
    final class Builder {

        private final Operator target;
        private final Instant since;
        private final Actor actor;
        private @Nullable String reason;
        private Visibility visibility = Visibility.NORMAL;

        private Builder(final Operator target, final Instant since, final Actor actor) {
            this.target = Objects.requireNonNull(target, "target");
            this.since = Objects.requireNonNull(since, "since");
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
        public RollbackRequest build() {
            record Impl(Operator target, Instant since, Actor actor, Optional<String> reason,
                    Visibility visibility) implements RollbackRequest {
            }

            ValidationException.throwIfAny(this.validate());
            return new Impl(this.target, this.since, this.actor, Optional.ofNullable(this.reason),
                    this.visibility);
        }
    }
}
