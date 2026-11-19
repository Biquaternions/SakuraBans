package net.serlith.sakurabans.api.event;

import java.util.List;
import net.serlith.sakurabans.api.wave.BanWave;
import net.serlith.sakurabans.api.wave.BanWaveEntry;

/**
 * After a ban wave was released and every entry has a result. Each created punishment also fired
 * its own {@link PunishmentCreatedEvent}.
 *
 * <p>Runs on a SakuraBans worker thread.
 */
public interface BanWaveReleasedEvent extends SyncedEvent {

    /**
     * The wave, now {@code RELEASED}.
     *
     * @return the wave
     */
    BanWave wave();

    /**
     * Its entries with their results.
     *
     * @return the entries
     */
    List<BanWaveEntry> entries();
}
