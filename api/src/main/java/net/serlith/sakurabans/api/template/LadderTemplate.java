package net.serlith.sakurabans.api.template;

import java.time.Duration;
import java.util.List;
import java.util.Optional;

/**
 * A ladder: escalating steps, each naming its own punishment type or none. The victim's
 * progress is the number of earlier uses of the ladder (and the weighted uses of its group
 * mates); it picks the step. Implemented by SakuraBans; don't implement it.
 */
public non-sealed interface LadderTemplate extends TemplateInfo {

    /** The most steps a ladder may have. */
    int MAX_STEPS = 64;

    /**
     * The steps: the first use applies step 0, the next one step 1, and so on; past the end,
     * the last step repeats.
     *
     * @return the steps, never empty
     */
    List<TemplateStep> steps();

    /**
     * The step for a number of earlier uses, capped at the last step.
     *
     * @param index the step, counted from 0
     * @return the step
     */
    default TemplateStep step(final int index) {
        final List<TemplateStep> steps = this.steps();
        return steps.get(Math.clamp(index, 0, steps.size() - 1));
    }

    /**
     * How long a use counts towards the ladder (LiteBans' {@code expire_ladder}): older ones are
     * left out when the step is counted.
     *
     * @return the window, or empty if every earlier use counts
     */
    Optional<Duration> expiry();

    /**
     * Whether progress is shared through addresses (LiteBans' {@code ip_template}): earlier uses
     * on the victim's alts and addresses count too.
     *
     * @return {@code true} if shared
     */
    boolean addressShared();
}
