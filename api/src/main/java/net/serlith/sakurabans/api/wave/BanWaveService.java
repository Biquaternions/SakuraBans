package net.serlith.sakurabans.api.wave;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import net.serlith.sakurabans.api.admin.AdminResult;
import net.serlith.sakurabans.api.model.Actor;
import net.serlith.sakurabans.api.punish.DraftPunishment;
import net.serlith.sakurabans.api.punish.PunishOptions;
import org.jspecify.annotations.Nullable;

/**
 * Ban waves. Waves are stored in the database; a release runs on one instance under a lease and
 * punishes every entry through the normal punishment flow (events, limits and exemption
 * included). Every method returns a future completed on a worker thread. Implemented by
 * SakuraBans; don't implement it.
 */
public interface BanWaveService {

    /**
     * The waves with some statuses.
     *
     * @param statuses the statuses, or an empty set for every wave
     * @return the waves, newest first
     */
    CompletableFuture<List<BanWave>> waves(Set<BanWave.Status> statuses);

    /**
     * One wave.
     *
     * @param id the wave's id
     * @return the wave, or empty
     */
    CompletableFuture<Optional<BanWave>> wave(long id);

    /**
     * A wave's entries.
     *
     * @param waveId the wave's id
     * @return the entries, oldest first
     */
    CompletableFuture<List<BanWaveEntry>> entries(long waveId);

    /**
     * Creates an empty wave in {@code DRAFT}.
     *
     * @param name the name, 1-64 characters
     * @param actor who creates it
     * @return the new wave
     */
    CompletableFuture<AdminResult<BanWave>> create(String name, Actor actor);

    /**
     * Adds a punishment to a wave that hasn't been released or cancelled.
     *
     * @param waveId the wave's id
     * @param draft the punishment to create on release
     * @param options the options to create it with
     * @param actor who adds it
     * @return the new entry
     */
    CompletableFuture<AdminResult<BanWaveEntry>> addEntry(
            long waveId, DraftPunishment draft, PunishOptions options, Actor actor);

    /**
     * Removes an entry from a wave that hasn't been released or cancelled.
     *
     * @param waveId the wave's id
     * @param entryId the entry's id
     * @param actor who removes it
     * @return the removed entry
     */
    CompletableFuture<AdminResult<BanWaveEntry>> removeEntry(
            long waveId, long entryId, Actor actor);

    /**
     * Schedules a wave, or unschedules it.
     *
     * @param waveId the wave's id
     * @param at when to release it, in the future, or {@code null} to release it manually
     * @param expectedVersion the version the caller last saw
     * @param actor who schedules it
     * @return the changed wave
     */
    CompletableFuture<AdminResult<BanWave>> schedule(
            long waveId, @Nullable Instant at, long expectedVersion, Actor actor);

    /**
     * Releases a wave now. The future completes when every entry has a result.
     *
     * @param waveId the wave's id
     * @param expectedVersion the version the caller last saw
     * @param actor who releases it
     * @return the released wave
     */
    CompletableFuture<AdminResult<BanWave>> release(long waveId, long expectedVersion, Actor actor);

    /**
     * Cancels a wave that hasn't been released.
     *
     * @param waveId the wave's id
     * @param expectedVersion the version the caller last saw
     * @param actor who cancels it
     * @return the cancelled wave
     */
    CompletableFuture<AdminResult<BanWave>> cancel(long waveId, long expectedVersion, Actor actor);
}
