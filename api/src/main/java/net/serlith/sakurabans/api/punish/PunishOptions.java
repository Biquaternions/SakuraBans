package net.serlith.sakurabans.api.punish;

import java.util.List;
import java.util.Objects;
import net.serlith.sakurabans.api.model.Visibility;
import net.serlith.sakurabans.api.validation.FieldError;

/**
 * How {@link PunishmentService#punish(DraftPunishment, PunishOptions)} behaves.
 */
public interface PunishOptions {

    /**
     * Who is told about the punishment.
     *
     * @return the visibility; {@link Visibility#NORMAL} by default
     */
    Visibility visibility();

    /**
     * What to do with an existing active punishment of the same type and scope.
     *
     * @return the policy; {@link OverridePolicy#REJECT} by default
     */
    OverridePolicy override();

    /**
     * Whether it takes effect right away: online victims are kicked or muted on every instance.
     * Without it, the punishment is only recorded and applies from the victim's next check.
     *
     * @return {@code true} by default
     */
    boolean enforce();

    /**
     * Whether the operator's duration limits and cooldowns are ignored.
     *
     * @return {@code false} by default
     */
    boolean bypassLimits();

    /**
     * A builder that starts with these options.
     *
     * @return a new builder
     */
    default Builder toBuilder() {
        return builder()
                .visibility(this.visibility())
                .override(this.override())
                .enforce(this.enforce())
                .bypassLimits(this.bypassLimits());
    }

    /**
     * The default options.
     *
     * @return normal visibility, reject, enforced right away, limits applied
     */
    static PunishOptions defaults() {
        return builder().build();
    }

    /**
     * Starts with the default options.
     *
     * @return a new builder
     */
    static Builder builder() {
        return new Builder();
    }

    /**
     * Builds {@link PunishOptions}. Not thread-safe.
     */
    final class Builder {

        private Visibility visibility = Visibility.NORMAL;
        private OverridePolicy override = OverridePolicy.REJECT;
        private boolean enforce = true;
        private boolean bypassLimits;

        private Builder() {
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
         * Sets the override policy.
         *
         * @param override what to do with an existing punishment
         * @return this builder
         */
        public Builder override(final OverridePolicy override) {
            this.override = Objects.requireNonNull(override, "override");
            return this;
        }

        /**
         * Sets whether it takes effect right away.
         *
         * @param enforce {@code false} to only record it
         * @return this builder
         */
        public Builder enforce(final boolean enforce) {
            this.enforce = enforce;
            return this;
        }

        /**
         * Sets whether limits and cooldowns are ignored.
         *
         * @param bypassLimits {@code true} to ignore them
         * @return this builder
         */
        public Builder bypassLimits(final boolean bypassLimits) {
            this.bypassLimits = bypassLimits;
            return this;
        }

        /**
         * Checks the values without building. Options can't be invalid, so this is always
         * empty; it exists so every builder can be validated the same way.
         *
         * @return no errors
         */
        public List<FieldError> validate() {
            return List.of();
        }

        /**
         * Builds the options.
         *
         * @return the options
         */
        public PunishOptions build() {
            record Impl(Visibility visibility, OverridePolicy override, boolean enforce,
                    boolean bypassLimits) implements PunishOptions {
                Impl {
                    Objects.requireNonNull(visibility, "visibility");
                    Objects.requireNonNull(override, "override");
                }
            }

            return new Impl(this.visibility, this.override, this.enforce, this.bypassLimits);
        }
    }
}
