package net.serlith.sakurabans.core.punish;

import java.time.InstantSource;
import java.util.concurrent.CompletableFuture;
import net.kyori.adventure.text.logger.slf4j.ComponentLogger;
import net.serlith.sakurabans.api.model.Actor;
import net.serlith.sakurabans.core.storage.repository.PunishmentResetRepository;
import net.serlith.sakurabans.core.sync.SyncPacket;
import net.serlith.sakurabans.core.sync.SyncService;
import net.serlith.sakurabans.core.util.Futures;

public final class ResetService {

    static final int BATCH_SIZE = 1_000;

    private final PunishmentResetRepository repository;
    private final MuteCache mutes;
    private final ActiveRangeCache ranges;
    private final PunishmentCounts counts;
    private final SyncService sync;
    private final InstantSource clock;
    private final ComponentLogger logger;

    public ResetService(final PunishmentResetRepository repository, final MuteCache mutes,
            final ActiveRangeCache ranges, final PunishmentCounts counts, final SyncService sync,
            final InstantSource clock, final ComponentLogger logger) {
        this.repository = repository;
        this.mutes = mutes;
        this.ranges = ranges;
        this.counts = counts;
        this.sync = sync;
        this.clock = clock;
        this.logger = logger;
    }

    public CompletableFuture<PunishmentResetRepository.Counts> preview(
            final PunishmentResetRepository.Filter filter) {
        return this.repository.count(filter);
    }

    /** Returns how many punishments were deleted or ended. */
    public CompletableFuture<Long> reset(final PunishmentResetRepository.Mode mode,
            final PunishmentResetRepository.Filter filter, final Actor actor) {
        return this.repository.run(mode, filter, actor, this.clock.instant(), BATCH_SIZE)
                .thenCompose(count -> {
                    if (count > 0) {
                        this.sync.publish(new SyncPacket.PunishmentsResetPacket());
                    }
                    // Even a failed refresh leaves the reset done; the caches reload on their
                    // own schedule, so it is only logged.
                    return Futures.logged(CompletableFuture.allOf(this.ranges.reload(),
                                    this.mutes.reloadAll(), this.counts.reloadAll()), this.logger,
                            "reload the caches after a reset").handle((ignored, failure) -> count);
                });
    }
}
