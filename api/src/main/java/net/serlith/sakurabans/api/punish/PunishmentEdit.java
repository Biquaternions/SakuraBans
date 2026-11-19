package net.serlith.sakurabans.api.punish;

import java.time.Instant;
import java.util.Optional;
import org.jspecify.annotations.Nullable;

/**
 * One change to an existing punishment, for
 * {@link PunishmentService#edit(long, PunishmentEdit, net.serlith.sakurabans.api.model.Operator)}.
 * Every edit is recorded in the audit log.
 */
public sealed interface PunishmentEdit permits PunishmentEdit.NewReason, PunishmentEdit.NewExpiry {

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
         */
        static NewReason of(final String reason) {
            record Impl(String reason) implements NewReason {
                Impl {
                    Validation.reason(reason);
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
}
