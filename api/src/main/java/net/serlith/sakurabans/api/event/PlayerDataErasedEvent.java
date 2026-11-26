package net.serlith.sakurabans.api.event;

import net.serlith.sakurabans.api.data.EraseResult;
import net.serlith.sakurabans.api.model.Actor;

/**
 * After a player's data was erased, so other plugins can erase what they keep about the player.
 * The account's punishments and UUID stay in SakuraBans; its names and addresses are gone, and
 * its victims keep only the hash of their addresses (see
 * {@link net.serlith.sakurabans.api.data.PlayerDataService#erase}). Punishments seen before the
 * erase may still carry the old victim; fetch them again.
 *
 * <p>Runs on a SakuraBans worker thread.
 */
public interface PlayerDataErasedEvent extends SakuraEvent {

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
