package net.serlith.sakurabans.api.punish;

import java.util.Objects;
import net.serlith.sakurabans.api.model.Visibility;

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
     * Where it takes effect right away.
     *
     * @return the enforcement; {@link Enforcement#GLOBAL} by default
     */
    Enforcement enforcement();

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
                .enforcement(this.enforcement())
                .bypassLimits(this.bypassLimits());
    }

    /**
     * The default options.
     *
     * @return normal visibility, reject, global enforcement, limits applied
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
        private Enforcement enforcement = Enforcement.GLOBAL;
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
         * Sets the enforcement.
         *
         * @param enforcement where it takes effect right away
         * @return this builder
         */
        public Builder enforcement(final Enforcement enforcement) {
            this.enforcement = Objects.requireNonNull(enforcement, "enforcement");
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
         * Builds the options.
         *
         * @return the options
         */
        public PunishOptions build() {
            record Impl(Visibility visibility, OverridePolicy override, Enforcement enforcement,
                    boolean bypassLimits) implements PunishOptions {
                Impl {
                    Objects.requireNonNull(visibility, "visibility");
                    Objects.requireNonNull(override, "override");
                    Objects.requireNonNull(enforcement, "enforcement");
                }
            }

            return new Impl(this.visibility, this.override, this.enforcement, this.bypassLimits);
        }
    }
}
