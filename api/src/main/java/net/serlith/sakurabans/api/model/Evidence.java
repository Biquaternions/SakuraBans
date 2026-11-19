package net.serlith.sakurabans.api.model;

import java.time.Instant;

/**
 * Evidence attached to a punishment. Implemented by SakuraBans; don't implement it.
 */
public interface Evidence {

    /**
     * What the evidence is.
     *
     * @return the reference
     */
    EvidenceRef ref();

    /**
     * Who attached it.
     *
     * @return the actor
     */
    Actor addedBy();

    /**
     * When it was attached.
     *
     * @return the instant
     */
    Instant addedAt();
}
