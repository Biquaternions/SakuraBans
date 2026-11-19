package net.serlith.sakurabans.api.punish;

import java.util.Objects;
import java.util.Optional;
import net.serlith.sakurabans.api.model.Operator;
import net.serlith.sakurabans.api.model.Visibility;
import org.jspecify.annotations.Nullable;

/**
 * A request to end punishments early (unban, unmute, unwarn). Build one with
 * {@link #builder(RevocationTarget, Operator)}.
 */
public interface RevocationRequest {

    /**
     * Which punishments to end.
     *
     * @return the target
     */
    RevocationTarget target();

    /**
     * Who ends them.
     *
     * @return the operator
     */
    Operator operator();

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
     * Whether only punishments issued by {@link #operator()} may be ended (the {@code .own}
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
        return builder(this.target(), this.operator())
                .reason(this.reason().orElse(null))
                .visibility(this.visibility())
                .ownOnly(this.ownOnly());
    }

    /**
     * Starts a request.
     *
     * @param target which punishments to end
     * @param operator who ends them
     * @return a new builder
     */
    static Builder builder(final RevocationTarget target, final Operator operator) {
        return new Builder(target, operator);
    }

    /**
     * Builds a {@link RevocationRequest}. Not thread-safe.
     */
    final class Builder {

        private final RevocationTarget target;
        private final Operator operator;
        private @Nullable String reason;
        private Visibility visibility = Visibility.NORMAL;
        private boolean ownOnly;

        private Builder(final RevocationTarget target, final Operator operator) {
            this.target = Objects.requireNonNull(target, "target");
            this.operator = Objects.requireNonNull(operator, "operator");
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
         * Builds the request.
         *
         * @return the request
         * @throws IllegalArgumentException if the reason is too long
         */
        public RevocationRequest build() {
            record Impl(RevocationTarget target, Operator operator, Optional<String> reason,
                    Visibility visibility, boolean ownOnly) implements RevocationRequest {
                Impl {
                    Objects.requireNonNull(target, "target");
                    Objects.requireNonNull(operator, "operator");
                    reason.ifPresent(Validation::reason);
                    Objects.requireNonNull(visibility, "visibility");
                }
            }

            return new Impl(this.target, this.operator, Optional.ofNullable(this.reason),
                    this.visibility, this.ownOnly);
        }
    }
}
