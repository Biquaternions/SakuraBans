package net.serlith.sakurabans.api.event;

import java.util.List;
import java.util.UUID;
import net.serlith.sakurabans.api.player.AltEntry;

/**
 * After the alt scan of a joining player finished on this instance, whether or not staff are
 * notified. Not fired when the scan finds nothing.
 *
 * <p>Runs on a SakuraBans worker thread.
 */
public interface AltsDetectedEvent extends SakuraEvent {

    /**
     * The player who joined.
     *
     * @return the account's UUID
     */
    UUID player();

    /**
     * The linked accounts found, with the configured strictness.
     *
     * @return the alts, never empty
     */
    List<AltEntry> alts();
}
