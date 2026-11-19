package net.serlith.sakurabans.api.event;

import net.serlith.sakurabans.api.model.Punishment;
import net.serlith.sakurabans.api.punish.EditRequest;

/**
 * Before a punishment is edited. Cancelling keeps it unchanged ({@code CANCELLED}).
 *
 * <p>Runs on a SakuraBans worker thread.
 */
public interface PunishmentEditEvent extends Cancellable {

    /**
     * The punishment before the edit.
     *
     * @return the punishment
     */
    Punishment punishment();

    /**
     * The edit.
     *
     * @return the request
     */
    EditRequest request();
}
