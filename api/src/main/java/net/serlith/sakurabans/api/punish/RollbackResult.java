package net.serlith.sakurabans.api.punish;

import java.util.List;
import net.serlith.sakurabans.api.model.Punishment;

/**
 * The outcome of {@link PunishmentService#rollback(RollbackRequest)}. Implemented by SakuraBans;
 * don't implement it.
 */
public sealed interface RollbackResult permits RollbackResult.Success, RollbackResult.Failure {

    /**
     * The rollback ran.
     */
    non-sealed interface Success extends RollbackResult {

        /**
         * The punishments that were ended, as they are now.
         *
         * @return the ended punishments, possibly empty when nothing matched
         */
        List<Punishment> rolledBack();
    }

    /**
     * The rollback didn't run.
     */
    non-sealed interface Failure extends RollbackResult {

        /**
         * Why it didn't run.
         *
         * @return the reason
         */
        Reason reason();

        /**
         * Why a rollback didn't run.
         */
        enum Reason {

            /** No reason was given, and the configuration requires one. */
            REASON_REQUIRED,

            /** The actor isn't allowed to do this (permissions or an integration's guard). */
            FORBIDDEN
        }
    }
}
