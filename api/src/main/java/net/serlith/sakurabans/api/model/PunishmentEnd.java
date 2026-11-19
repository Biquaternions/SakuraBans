package net.serlith.sakurabans.api.model;

import java.time.Instant;
import java.util.Optional;

/**
 * How and when a punishment stopped being active. Implemented by SakuraBans; don't implement it.
 */
public interface PunishmentEnd {

    /**
     * Why it ended.
     *
     * @return the kind of end
     */
    EndKind kind();

    /**
     * When it ended.
     *
     * @return the instant, not before the punishment's creation
     */
    Instant at();

    /**
     * Who ended it. Present for {@link EndKind#REVOKED}, {@link EndKind#ROLLED_BACK},
     * {@link EndKind#PRUNED} and {@link EndKind#VOIDED}; empty when nobody did, as for
     * {@link EndKind#EXPIRED} and {@link EndKind#SUPERSEDED}.
     *
     * @return the actor, or empty
     */
    Optional<Actor> actor();

    /**
     * The reason given.
     *
     * @return the reason, at most {@link Lengths#MAX_REASON} characters, or empty
     */
    Optional<String> reason();
}
