package net.serlith.sakurabans.api.punish;

import net.serlith.sakurabans.api.model.Punishment;

/**
 * The outcome of {@link PunishmentService#voidPunishment(long,
 * net.serlith.sakurabans.api.model.Actor, String)}. Implemented by SakuraBans; don't implement
 * it.
 */
public sealed interface VoidResult permits VoidResult.Success, VoidResult.Failure {

    /**
     * The punishment was voided.
     */
    non-sealed interface Success extends VoidResult {

        /**
         * The punishment after voiding; it ended with {@code VOIDED} if it was active.
         *
         * @return the punishment
         */
        Punishment punishment();
    }

    /**
     * Nothing was voided.
     */
    non-sealed interface Failure extends VoidResult {

        /**
         * Why nothing was voided.
         *
         * @return the reason
         */
        Reason reason();

        /**
         * Why nothing was voided.
         */
        enum Reason {

            /** No punishment has this id. */
            NOT_FOUND,

            /** It is already voided. */
            ALREADY_VOIDED,

            /** The punishment's operator is protected from this actor (exemption). */
            EXEMPT,

            /** An event listener cancelled it. */
            CANCELLED,

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
