package net.serlith.sakurabans.api.event;

import net.serlith.sakurabans.api.data.EraseResult;
import net.serlith.sakurabans.api.model.Actor;

/**
 * After a player's data was erased, so other plugins can erase what they keep about the player.
 *
 * <p>Runs on a SakuraBans worker thread.
 */
public interface PlayerDataErasedEvent extends SyncedEvent {

    /**
     * What was erased.
     *
     * @return the result, including the account's UUID
     */
    EraseResult result();

    /**
     * Who requested it.
     *
     * @return the actor
     */
    Actor actor();
}
