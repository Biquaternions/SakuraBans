package net.serlith.sakurabans.api.punish;

import java.util.Optional;
import net.serlith.sakurabans.api.model.Punishment;

/**
 * The outcome of {@link PunishmentService#punish(DraftPunishment, PunishOptions)}. Implemented by
 * SakuraBans; don't implement it.
 */
public sealed interface PunishResult permits PunishResult.Success, PunishResult.Failure {

    /**
     * The punishment was created.
     */
    non-sealed interface Success extends PunishResult {

        /**
         * The new punishment.
         *
         * @return the punishment
         */
        Punishment punishment();

        /**
         * The punishment it replaced, when the override policy allowed a replacement.
         *
         * @return the ended punishment, or empty
         */
        Optional<Punishment> superseded();
    }

    /**
     * The punishment was not created.
     */
    non-sealed interface Failure extends PunishResult {

        /**
         * Why it wasn't created.
         *
         * @return the reason
         */
        Reason reason();

        /**
         * The active punishment that blocked it, for {@link Reason#ALREADY_PUNISHED}.
         *
         * @return the existing punishment, or empty
         */
        Optional<Punishment> conflicting();

        /**
         * Why a punishment wasn't created.
         */
        enum Reason {

            /** The victim already has one and the override policy kept it. */
            ALREADY_PUNISHED,

            /** The victim is exempt from punishments by this operator. */
            EXEMPT,

            /** A {@code PunishmentCreateEvent} listener cancelled it. */
            CANCELLED,

            /** The duration exceeds the operator's limit and reducing it is turned off. */
            LIMIT_EXCEEDED,

            /** The operator is still in a cooldown for this type. */
            COOLDOWN,

            /** No reason was given, and the configuration requires one. */
            REASON_REQUIRED,

            /** No template was given, and the operator's limit group requires one. */
            TEMPLATE_REQUIRED,

            /** The template doesn't exist or doesn't match the type. */
            UNKNOWN_TEMPLATE,

            /** The kind doesn't exist or its base type differs from the draft's type. */
            UNKNOWN_KIND,

            /** The template or kind requires evidence of a type the draft doesn't have. */
            EVIDENCE_REQUIRED,

            /** The actor isn't allowed to do this (permissions or an integration's guard). */
            FORBIDDEN
        }
    }
}
