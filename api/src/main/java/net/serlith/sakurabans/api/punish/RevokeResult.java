package net.serlith.sakurabans.api.punish;

import java.util.List;
import net.serlith.sakurabans.api.model.Punishment;

/**
 * The outcome of {@link PunishmentService#revoke(RevocationRequest)}. Implemented by SakuraBans;
 * don't implement it.
 */
public sealed interface RevokeResult permits RevokeResult.Success, RevokeResult.Failure {

    /**
     * At least one punishment was ended.
     */
    non-sealed interface Success extends RevokeResult {

        /**
         * The punishments that were ended, as they are now. More than one only for a
         * {@link RevocationTarget.ByVictim} target over every scope, or for warnings.
         *
         * @return the ended punishments, never empty
         */
        List<Punishment> revoked();
    }

    /**
     * Nothing was ended.
     */
    non-sealed interface Failure extends RevokeResult {

        /**
         * Why nothing was ended.
         *
         * @return the reason
         */
        Reason reason();

        /**
         * Why nothing was ended.
         */
        enum Reason {

            /** No matching active punishment. */
            NOT_FOUND,

            /** The request was {@code ownOnly}, and the punishment was issued by someone else. */
            NOT_OWN,

            /** The punishment's operator is protected from this operator (exemption). */
            EXEMPT,

            /** A {@code PunishmentRevokeEvent} listener cancelled it. */
            CANCELLED,

            /** No reason was given, and the configuration requires one. */
            REASON_REQUIRED,

            /** The actor isn't allowed to do this (permissions or an integration's guard). */
            FORBIDDEN
        }
    }
}
