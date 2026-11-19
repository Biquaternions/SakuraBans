package net.serlith.sakurabans.api.event;

import net.serlith.sakurabans.api.model.Evidence;
import net.serlith.sakurabans.api.model.Punishment;

/**
 * After evidence was attached to a punishment.
 *
 * <p>Runs on a SakuraBans worker thread.
 */
public interface EvidenceAttachedEvent extends SyncedEvent {

    /**
     * The punishment, as it is now.
     *
     * @return the punishment
     */
    Punishment punishment();

    /**
     * The evidence.
     *
     * @return the evidence
     */
    Evidence evidence();
}
