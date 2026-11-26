package net.serlith.sakurabans.api.event;

import net.serlith.sakurabans.api.model.Punishment;

/**
 * After a punishment was deleted from the history.
 *
 * <p>Runs on a SakuraBans worker thread.
 */
public interface PunishmentExpungedEvent extends SakuraEvent {

    /**
     * The punishment as it was before it was deleted.
     *
     * @return the deleted punishment
     */
    Punishment punishment();
}
