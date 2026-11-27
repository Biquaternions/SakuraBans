package net.serlith.sakurabans.api.punish;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import net.serlith.sakurabans.api.model.Visibility;
import net.serlith.sakurabans.api.validation.FieldError;
import net.serlith.sakurabans.api.validation.Validator;
import org.jspecify.annotations.Nullable;

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
     * The server stored as the punishment's origin, instead of this server's own name.
     *
     * @return the server name (a lower-case identifier), or empty to use this server's name
     */
    Optional<String> originServer();

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
                .bypassLimits(this.bypassLimits())
                .originServer(this.originServer().orElse(null));
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
        private @Nullable String originServer;

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
         * Sets the origin server stored with the punishment.
         *
         * @param originServer a server name (1-64 characters of a-z, 0-9, '_' and '-', in any
         *         case), or {@code null} for this server's own name
         * @return this builder
         */
        public Builder originServer(final @Nullable String originServer) {
            this.originServer = originServer;
            return this;
        }

        /**
         * Checks the values without building.
         *
         * @return the errors, empty if the options are valid
         */
        public List<FieldError> validate() {
            return Validator.create().identifier("originServer", this.originServer).errors();
        }

        /**
         * Builds the options.
         *
         * @return the options
         */
        public PunishOptions build() {
            record Impl(Visibility visibility, OverridePolicy override, boolean enforce,
                    boolean bypassLimits, Optional<String> originServer)
                    implements PunishOptions {
                Impl {
                    Objects.requireNonNull(visibility, "visibility");
                    Objects.requireNonNull(override, "override");
                }
            }

            Validator.create().identifier("originServer", this.originServer).throwIfAny();
            return new Impl(this.visibility, this.override, this.enforce, this.bypassLimits,
                    Optional.ofNullable(this.originServer)
                            .flatMap(Validator::normalizeIdentifier));
        }
    }
}
