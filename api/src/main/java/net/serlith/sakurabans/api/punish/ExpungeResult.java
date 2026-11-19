package net.serlith.sakurabans.api.punish;

import net.serlith.sakurabans.api.model.Punishment;

/**
 * The outcome of
 * {@link PunishmentService#expunge(long, net.serlith.sakurabans.api.model.Operator)}. Implemented
 * by SakuraBans; don't implement it.
 */
public sealed interface ExpungeResult permits ExpungeResult.Success, ExpungeResult.Failure {

    /**
     * The punishment was deleted.
     */
    non-sealed interface Success extends ExpungeResult {

        /**
         * The punishment as it was before it was deleted.
         *
         * @return the deleted punishment
         */
        Punishment expunged();
    }

    /**
     * Nothing was deleted.
     */
    non-sealed interface Failure extends ExpungeResult {

        /**
         * Why nothing was deleted.
         *
         * @return the reason
         */
        Reason reason();

        /**
         * Why nothing was deleted.
         */
        enum Reason {

            /** No punishment has this id. */
            NOT_FOUND,

            /** The punishment's operator is protected from this operator (exemption). */
            EXEMPT,

            /** An event listener cancelled it. */
            CANCELLED
        }
    }
}
