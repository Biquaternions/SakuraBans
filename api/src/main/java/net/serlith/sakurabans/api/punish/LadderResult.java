package net.serlith.sakurabans.api.punish;

import java.util.Optional;
import net.serlith.sakurabans.api.model.Punishment;

/**
 * The outcome of {@link PunishmentService#applyLadder(LadderRequest, PunishOptions)}.
 * Implemented by SakuraBans; don't implement it.
 */
public sealed interface LadderResult
        permits LadderResult.Punished, LadderResult.ActionsOnly, LadderResult.Failure {

    /**
     * The step created a punishment; its use counts on the ladder.
     */
    non-sealed interface Punished extends LadderResult {

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

        /**
         * The step that was applied.
         *
         * @return the step, counted from 0
         */
        int step();
    }

    /**
     * The step has no punishment: only its actions ran. Its use counts on the ladder.
     */
    non-sealed interface ActionsOnly extends LadderResult {

        /**
         * The step that was applied.
         *
         * @return the step, counted from 0
         */
        int step();
    }

    /**
     * Nothing was applied and nothing counts.
     */
    non-sealed interface Failure extends LadderResult {

        /**
         * Why: {@code UNKNOWN_TEMPLATE} for no template of that name,
         * {@code TEMPLATE_TYPE_MISMATCH} for a preset, or why the step's punishment wasn't
         * created ({@code FORBIDDEN}, {@code EXEMPT}, {@code COOLDOWN}, {@code CANCELLED},
         * {@code ALREADY_PUNISHED}, …).
         *
         * @return the reason
         */
        PunishResult.Failure.Reason reason();

        /**
         * The active punishment that blocked it, for {@code ALREADY_PUNISHED}.
         *
         * @return the existing punishment, or empty
         */
        Optional<Punishment> conflicting();
    }
}
