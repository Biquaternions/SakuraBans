package net.serlith.sakurabans.api.event;

import net.serlith.sakurabans.api.model.Punishment;
import net.serlith.sakurabans.api.punish.PunishmentEdit;

/**
 * After a punishment was edited.
 *
 * <p>Runs on a SakuraBans worker thread.
 */
public interface PunishmentEditedEvent extends SyncedEvent {

    /**
     * The punishment before the edit.
     *
     * @return the previous state
     */
    Punishment previous();

    /**
     * The punishment after the edit.
     *
     * @return the new state
     */
    Punishment punishment();

    /**
     * What changed.
     *
     * @return the edit
     */
    PunishmentEdit edit();
}
