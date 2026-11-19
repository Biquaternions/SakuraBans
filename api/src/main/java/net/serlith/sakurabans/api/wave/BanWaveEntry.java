package net.serlith.sakurabans.api.wave;

import java.time.Instant;
import java.util.Optional;
import net.serlith.sakurabans.api.model.Actor;
import net.serlith.sakurabans.api.punish.DraftPunishment;
import net.serlith.sakurabans.api.punish.PunishOptions;
import net.serlith.sakurabans.api.punish.PunishResult;

/**
 * A punishment waiting in a ban wave. Implemented by SakuraBans; don't implement it.
 */
public interface BanWaveEntry {

    /**
     * The entry's id.
     *
     * @return the id
     */
    long id();

    /**
     * The wave it belongs to.
     *
     * @return the wave's id
     */
    long waveId();

    /**
     * The punishment to create on release. Its duration counts from the release.
     *
     * @return the draft
     */
    DraftPunishment draft();

    /**
     * The options to create it with.
     *
     * @return the options
     */
    PunishOptions options();

    /**
     * Who added it.
     *
     * @return the actor
     */
    Actor addedBy();

    /**
     * When it was added.
     *
     * @return the instant
     */
    Instant addedAt();

    /**
     * What happened on release.
     *
     * @return the result, or empty before the release
     */
    Optional<PunishResult> result();
}
