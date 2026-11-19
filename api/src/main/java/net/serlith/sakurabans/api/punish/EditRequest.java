package net.serlith.sakurabans.api.punish;

import java.util.List;
import java.util.Objects;
import java.util.OptionalLong;
import net.serlith.sakurabans.api.model.Actor;
import net.serlith.sakurabans.api.validation.FieldError;
import net.serlith.sakurabans.api.validation.FieldError.Code;
import net.serlith.sakurabans.api.validation.ValidationException;
import net.serlith.sakurabans.api.validation.Validator;

/**
 * A request to change one aspect of a punishment, for
 * {@link PunishmentService#edit(EditRequest)}. Build one with
 * {@link #builder(long, PunishmentEdit, Actor)}.
 */
public interface EditRequest {

    /**
     * The punishment to change.
     *
     * @return its database id
     */
    long id();

    /**
     * The change.
     *
     * @return the edit
     */
    PunishmentEdit edit();

    /**
     * Who changes it, and through which surface.
     *
     * @return the actor
     */
    Actor actor();

    /**
     * The version the caller last saw. If the punishment changed since, the edit fails with
     * {@code CONFLICT} and nothing changes.
     *
     * @return the expected version, or empty to change whatever is current
     */
    OptionalLong expectedVersion();

    /**
     * A builder that starts with this request's values.
     *
     * @return a new builder
     */
    default Builder toBuilder() {
        final Builder builder = builder(this.id(), this.edit(), this.actor());
        this.expectedVersion().ifPresent(builder::expectedVersion);
        return builder;
    }

    /**
     * Starts a request.
     *
     * @param id the punishment's database id
     * @param edit the change
     * @param actor who changes it
     * @return a new builder
     */
    static Builder builder(final long id, final PunishmentEdit edit, final Actor actor) {
        return new Builder(id, edit, actor);
    }

    /**
     * Builds an {@link EditRequest}. Not thread-safe.
     */
    final class Builder {

        private final long id;
        private final PunishmentEdit edit;
        private final Actor actor;
        private OptionalLong expectedVersion = OptionalLong.empty();

        private Builder(final long id, final PunishmentEdit edit, final Actor actor) {
            this.id = id;
            this.edit = Objects.requireNonNull(edit, "edit");
            this.actor = Objects.requireNonNull(actor, "actor");
        }

        /**
         * Sets the version the caller last saw.
         *
         * @param expectedVersion the version, at least 1
         * @return this builder
         */
        public Builder expectedVersion(final long expectedVersion) {
            this.expectedVersion = OptionalLong.of(expectedVersion);
            return this;
        }

        /**
         * Checks the values without building.
         *
         * @return every problem: {@code id} or {@code expectedVersion} below 1
         */
        public List<FieldError> validate() {
            final Validator errors = Validator.create();
            errors.check(this.id >= 1, "id", Code.OUT_OF_RANGE, "must be at least 1: " + this.id);
            this.expectedVersion.ifPresent(version -> errors.check(version >= 1,
                    "expectedVersion", Code.OUT_OF_RANGE, "must be at least 1: " + version));
            return errors.errors();
        }

        /**
         * Builds the request.
         *
         * @return the request
         * @throws ValidationException with every problem {@link #validate()} reports
         */
        public EditRequest build() {
            record Impl(long id, PunishmentEdit edit, Actor actor, OptionalLong expectedVersion)
                    implements EditRequest {
            }

            ValidationException.throwIfAny(this.validate());
            return new Impl(this.id, this.edit, this.actor, this.expectedVersion);
        }
    }
}
