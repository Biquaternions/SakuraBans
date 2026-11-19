package net.serlith.sakurabans.api.punish;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import net.serlith.sakurabans.api.model.Lengths;
import net.serlith.sakurabans.api.model.Scope;
import net.serlith.sakurabans.api.validation.Validator;
import org.jspecify.annotations.Nullable;

/**
 * One change to an existing punishment, sent through an {@link EditRequest}. Every edit is
 * recorded in the audit log and raises the punishment's version.
 */
public sealed interface PunishmentEdit permits PunishmentEdit.NewReason, PunishmentEdit.NewExpiry,
        PunishmentEdit.NewScope {

    /**
     * Replaces the reason. Allowed on active and ended punishments.
     */
    non-sealed interface NewReason extends PunishmentEdit {

        /**
         * The new reason.
         *
         * @return the reason, plain text
         */
        String reason();

        /**
         * Creates the edit.
         *
         * @param reason the new reason, at most
         *         {@link net.serlith.sakurabans.api.model.Lengths#MAX_REASON} characters
         * @return the edit
         * @throws net.serlith.sakurabans.api.validation.ValidationException if the reason is too
         *         long
         */
        static NewReason of(final String reason) {
            record Impl(String reason) implements NewReason {
                Impl {
                    Objects.requireNonNull(reason, "reason");
                    final Validator errors = Validator.create();
                    errors.maxLength("reason", reason, Lengths.MAX_REASON);
                    errors.throwIfAny();
                }
            }

            return new Impl(reason);
        }
    }

    /**
     * Replaces the expiry. Allowed on active punishments only; never on a kick.
     */
    non-sealed interface NewExpiry extends PunishmentEdit {

        /**
         * The new expiry.
         *
         * @return the instant, or empty to make it permanent
         */
        Optional<Instant> expiresAt();

        /**
         * Creates the edit. To extend a punishment, pass its current expiry plus the extra
         * time.
         *
         * @param expiresAt the new expiry, after the punishment's creation, or {@code null} for
         *         permanent
         * @return the edit
         */
        static NewExpiry of(final @Nullable Instant expiresAt) {
            record Impl(Optional<Instant> expiresAt) implements NewExpiry {
            }

            return new Impl(Optional.ofNullable(expiresAt));
        }
    }

    /**
     * Moves the punishment to another scope. Allowed on active punishments only; it fails with
     * {@code CONFLICT} if the victim already has an active punishment of the same type there.
     */
    non-sealed interface NewScope extends PunishmentEdit {

        /**
         * The new scope.
         *
         * @return the scope
         */
        Scope scope();

        /**
         * Creates the edit.
         *
         * @param scope the new scope
         * @return the edit
         */
        static NewScope of(final Scope scope) {
            record Impl(Scope scope) implements NewScope {
                Impl {
                    Objects.requireNonNull(scope, "scope");
                }
            }

            return new Impl(scope);
        }
    }
}
