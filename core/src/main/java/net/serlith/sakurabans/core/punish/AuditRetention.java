package net.serlith.sakurabans.core.punish;

import java.time.Duration;
import java.time.Instant;
import java.time.InstantSource;
import java.util.concurrent.CompletableFuture;
import net.kyori.adventure.text.logger.slf4j.ComponentLogger;
import net.serlith.sakurabans.core.configuration.ConfigService;
import net.serlith.sakurabans.core.storage.repository.AuditRepository;

public final class AuditRetention {

    static final Duration INTERVAL = Duration.ofHours(1);
    static final int BATCH = 1000;
    private static final int MAX_BATCHES = 50;

    private final AuditRepository audit;
    private final ConfigService config;
    private final InstantSource clock;
    private final ComponentLogger logger;
    private volatile Instant lastRun = Instant.MIN;

    public AuditRetention(final AuditRepository audit, final ConfigService config,
            final InstantSource clock, final ComponentLogger logger) {
        this.audit = audit;
        this.config = config;
        this.clock = clock;
        this.logger = logger;
    }

    /** The number of entries deleted; 0 when nothing is due. */
    public CompletableFuture<Long> pruneIfDue() {
        final Duration retention = this.config.settings().audit().retention();
        final Instant now = this.clock.instant();
        if (retention == null || now.isBefore(this.lastRun.plus(INTERVAL))) {
            return CompletableFuture.completedFuture(0L);
        }
        this.lastRun = now;
        return this.prune(now.minus(retention), 0, 0L).thenApply(count -> {
            if (count > 0) {
                this.logger.info("Deleted {} audit entries older than {} (audit.retention).",
                        count, retention);
            }
            return count;
        });
    }

    private CompletableFuture<Long> prune(final Instant cutoff, final int batches,
            final long done) {
        return this.audit.prune(cutoff, BATCH).thenCompose(count -> {
            final long total = done + count;
            return count < BATCH || batches + 1 >= MAX_BATCHES
                    ? CompletableFuture.completedFuture(total)
                    : this.prune(cutoff, batches + 1, total);
        });
    }
}
