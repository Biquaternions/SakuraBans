package net.serlith.sakurabans.api.punish;

import java.util.OptionalLong;
import net.serlith.sakurabans.api.model.Punishment;

/**
 * The outcome of {@link PunishmentService#edit(EditRequest)} and of the evidence operations.
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
         * The punishment's current version, for {@link Reason#CONFLICT}.
         *
         * @return the version, or empty for other reasons
         */
        OptionalLong currentVersion();

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
            CANCELLED,

            /**
             * The punishment changed since the expected version, or the new scope already has
             * an active punishment of the same type for the victim.
             */
            CONFLICT,

            /** The evidence is already attached. */
            EVIDENCE_ALREADY_ATTACHED,

            /** The evidence to detach isn't attached. */
            EVIDENCE_NOT_ATTACHED,

            /** The actor isn't allowed to do this (permissions or an integration's guard). */
            FORBIDDEN
        }
    }
}
