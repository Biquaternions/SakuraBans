package net.serlith.sakurabans.api.event;

import net.serlith.sakurabans.api.model.Actor;
import net.serlith.sakurabans.api.model.Punishment;

/**
 * Before a punishment is deleted. Cancelling keeps it ({@code CANCELLED}).
 *
 * <p>Runs on a SakuraBans worker thread.
 */
public interface PunishmentExpungeEvent extends Cancellable {

    /**
     * The punishment.
     *
     * @return the punishment
     */
    Punishment punishment();

    /**
     * Who deletes it.
     *
     * @return the actor
     */
    Actor actor();
}
