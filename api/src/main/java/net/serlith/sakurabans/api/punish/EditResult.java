package net.serlith.sakurabans.api.punish;

import net.serlith.sakurabans.api.model.Punishment;

/**
 * The outcome of
 * {@link PunishmentService#edit(long, PunishmentEdit, net.serlith.sakurabans.api.model.Operator)}.
 * Implemented by SakuraBans; don't implement it.
 */
public sealed interface EditResult permits EditResult.Success, EditResult.Failure {

    /**
     * The punishment was changed.
     */
    non-sealed interface Success extends EditResult {

        /**
         * The punishment after the change.
         *
         * @return the punishment
         */
        Punishment punishment();
    }

    /**
     * Nothing was changed.
     */
    non-sealed interface Failure extends EditResult {

        /**
         * Why nothing was changed.
         *
         * @return the reason
         */
        Reason reason();

        /**
         * Why nothing was changed.
         */
        enum Reason {

            /** No punishment has this id. */
            NOT_FOUND,

            /** The expiry of an ended punishment or of a kick can't change. */
            NOT_ACTIVE,

            /** The new expiry is not after the punishment's creation. */
            INVALID_EXPIRY,

            /** The punishment's operator is protected from this operator (exemption). */
            EXEMPT,

            /** An event listener cancelled it. */
            CANCELLED
        }
    }
}
