package net.serlith.sakurabans.api.model;

import java.time.Instant;
import java.util.Optional;

/**
 * How a punishment was voided: marked as a mistake. Implemented by SakuraBans; don't implement it.
 */
public interface PunishmentVoid {

    /**
     * When it was voided.
     *
     * @return the instant
     */
    Instant at();

    /**
     * Who voided it.
     *
     * @return the actor
     */
    Actor actor();

    /**
     * Why.
     *
     * @return the reason, at most {@link Lengths#MAX_REASON} characters, or empty
     */
    Optional<String> reason();
}
