package net.serlith.sakurabans.api.kind;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import net.serlith.sakurabans.api.model.AddressStrictness;
import net.serlith.sakurabans.api.model.PunishmentType;
import net.serlith.sakurabans.api.model.Scope;
import net.serlith.sakurabans.api.validation.FieldError;
import net.serlith.sakurabans.api.validation.FieldError.Code;
import net.serlith.sakurabans.api.validation.ValidationException;
import net.serlith.sakurabans.api.validation.Validator;
import org.jspecify.annotations.Nullable;

/**
 * The values of a kind to create or update with {@link KindService}. Build one with
 * {@link #builder(String, PunishmentType)}, or from an existing kind with
 * {@link #builder(Kind)}.
 */
public interface KindDefinition {

    /** The longest display name. */
    int MAX_DISPLAY_NAME = 64;

    /**
     * The id.
     *
     * @return the id, lower case
     */
    String id();

    /**
     * The display name.
     *
     * @return the display name
     */
    String displayName();

    /**
     * The base type.
     *
     * @return the type
     */
    PunishmentType baseType();

    /**
     * Whether mutes are shadow mutes.
     *
     * @return {@code true} for shadow mutes
     */
    boolean shadow();

    /**
     * The strictness.
     *
     * @return the strictness, or empty for the configured one
     */
    Optional<AddressStrictness> strictness();

    /**
     * The default scope.
     *
     * @return the scope, or empty for the configured default
     */
    Optional<Scope> defaultScope();

    /**
     * The address mode.
     *
     * @return the mode
     */
    AddressMode addressMode();

    /**
     * Whether punishments are always permanent.
     *
     * @return {@code true} if permanent only
     */
    boolean permanentOnly();

    /**
     * Required evidence types.
     *
     * @return the types
     */
    Set<String> requiredEvidence();

    /**
     * Starts a definition.
     *
     * @param id the id: an identifier, in any case
     * @param baseType the base type
     * @return a new builder with the display name set to the id
     */
    static Builder builder(final String id, final PunishmentType baseType) {
        return new Builder(id, baseType);
    }

    /**
     * Starts a definition with an existing kind's values, to change some of them.
     *
     * @param kind the kind
     * @return a new builder
     */
    static Builder builder(final Kind kind) {
        return builder(kind.id(), kind.baseType())
                .displayName(kind.displayName())
                .shadow(kind.shadow())
                .strictness(kind.strictness().orElse(null))
                .defaultScope(kind.defaultScope().orElse(null))
                .addressMode(kind.addressMode())
                .permanentOnly(kind.permanentOnly())
                .requiredEvidence(kind.requiredEvidence());
    }

    /**
     * Builds a {@link KindDefinition}. Not thread-safe.
     */
    final class Builder {

        private final String id;
        private final PunishmentType baseType;
        private String displayName;
        private boolean shadow;
        private @Nullable AddressStrictness strictness;
        private @Nullable Scope defaultScope;
        private AddressMode addressMode = AddressMode.AS_GIVEN;
        private boolean permanentOnly;
        private Set<String> requiredEvidence = Set.of();

        private Builder(final String id, final PunishmentType baseType) {
            this.id = Objects.requireNonNull(id, "id");
            this.baseType = Objects.requireNonNull(baseType, "baseType");
            this.displayName = id;
        }

        /**
         * Sets the display name.
         *
         * @param displayName the name shown to staff
         * @return this builder
         */
        public Builder displayName(final String displayName) {
            this.displayName = Objects.requireNonNull(displayName, "displayName");
            return this;
        }

        /**
         * Sets whether mutes are shadow mutes.
         *
         * @param shadow {@code true} for shadow mutes (mutes only)
         * @return this builder
         */
        public Builder shadow(final boolean shadow) {
            this.shadow = shadow;
            return this;
        }

        /**
         * Sets the strictness.
         *
         * @param strictness the strictness, or {@code null} for the configured one
         * @return this builder
         */
        public Builder strictness(final @Nullable AddressStrictness strictness) {
            this.strictness = strictness;
            return this;
        }

        /**
         * Sets the default scope.
         *
         * @param defaultScope the scope, or {@code null} for the configured default
         * @return this builder
         */
        public Builder defaultScope(final @Nullable Scope defaultScope) {
            this.defaultScope = defaultScope;
            return this;
        }

        /**
         * Sets the address mode.
         *
         * @param addressMode how the victim is treated
         * @return this builder
         */
        public Builder addressMode(final AddressMode addressMode) {
            this.addressMode = Objects.requireNonNull(addressMode, "addressMode");
            return this;
        }

        /**
         * Sets whether punishments are always permanent.
         *
         * @param permanentOnly {@code true} for permanent only (not for kicks)
         * @return this builder
         */
        public Builder permanentOnly(final boolean permanentOnly) {
            this.permanentOnly = permanentOnly;
            return this;
        }

        /**
         * Sets the required evidence types.
         *
         * @param requiredEvidence {@code <namespace>:<type>} entries
         * @return this builder
         */
        public Builder requiredEvidence(final Set<String> requiredEvidence) {
            this.requiredEvidence = Set.copyOf(requiredEvidence);
            return this;
        }

        /**
         * Checks the values without building.
         *
         * @return every problem: {@code id} not an identifier, {@code displayName} blank or too
         *         long, {@code shadow} on a non-mute, {@code permanentOnly} on a kick, or a
         *         {@code requiredEvidence} entry that isn't an evidence type
         */
        public List<FieldError> validate() {
            final Validator errors = Validator.create()
                    .identifier("id", this.id)
                    .name("displayName", this.displayName, MAX_DISPLAY_NAME)
                    .check(!this.shadow || this.baseType == PunishmentType.MUTE, "shadow",
                            Code.NOT_ALLOWED, "only mutes can be shadow mutes")
                    .check(!this.permanentOnly || !this.baseType.instant(), "permanentOnly",
                            Code.NOT_ALLOWED, "a kick has no duration");
            this.requiredEvidence.forEach(type -> errors.evidenceType("requiredEvidence", type));
            return errors.errors();
        }

        /**
         * Builds the definition.
         *
         * @return the definition
         * @throws ValidationException with every problem {@link #validate()} reports
         */
        public KindDefinition build() {
            record Impl(String id, String displayName, PunishmentType baseType, boolean shadow,
                    Optional<AddressStrictness> strictness, Optional<Scope> defaultScope,
                    AddressMode addressMode, boolean permanentOnly, Set<String> requiredEvidence)
                    implements KindDefinition {
            }

            ValidationException.throwIfAny(this.validate());
            return new Impl(Validator.normalizeIdentifier(this.id).orElseThrow(),
                    this.displayName, this.baseType, this.shadow,
                    Optional.ofNullable(this.strictness), Optional.ofNullable(this.defaultScope),
                    this.addressMode, this.permanentOnly, this.requiredEvidence);
        }
    }
}
