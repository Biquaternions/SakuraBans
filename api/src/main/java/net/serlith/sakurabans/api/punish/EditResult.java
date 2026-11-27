package net.serlith.sakurabans.api.punish;

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
         * Why nothing was changed.
         */
        enum Reason {

            /** No punishment has this id. */
            NOT_FOUND,

            /** The expiry or scope of an ended punishment or of a kick can't change. */
            NOT_ACTIVE,

            /** The new expiry is not after the punishment's creation, or already passed. */
            INVALID_EXPIRY,

            /**
             * The victim already has an active punishment of this type in the new scope (only
             * bans and mutes; warnings stack).
             */
            ALREADY_PUNISHED,

            /** The punishment's operator is protected from this operator (exemption). */
            EXEMPT,

            /** An event listener cancelled it. */
            CANCELLED,

            /** The evidence is already attached. */
            EVIDENCE_ALREADY_ATTACHED,

            /** The evidence to detach isn't attached. */
            EVIDENCE_NOT_ATTACHED,

            /** The actor isn't allowed to do this (permissions or an integration's guard). */
            FORBIDDEN,

            /**
             * The punishment was made from a template with {@code permission-required}, and
             * the actor lacks {@code sakurabans.template.<name>}.
             */
            TEMPLATE_PROTECTED
        }
    }
}
