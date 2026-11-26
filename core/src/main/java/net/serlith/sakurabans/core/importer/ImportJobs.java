package net.serlith.sakurabans.core.importer;

import java.sql.SQLException;
import java.time.Duration;
import java.time.InstantSource;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;
import net.kyori.adventure.text.logger.slf4j.ComponentLogger;
import net.serlith.sakurabans.api.imports.ImportOptions;
import net.serlith.sakurabans.api.imports.ImportedPunishment;
import net.serlith.sakurabans.api.model.Actor;
import net.serlith.sakurabans.api.punish.Enforcement;
import net.serlith.sakurabans.core.configuration.ConfigException;
import net.serlith.sakurabans.core.configuration.ConfigIssue;
import net.serlith.sakurabans.core.configuration.ConfigService;
import net.serlith.sakurabans.core.configuration.settings.ImportSettings;
import net.serlith.sakurabans.core.enforce.SyncReceiver;
import net.serlith.sakurabans.core.model.SakuraImportReport;
import net.serlith.sakurabans.core.storage.Lease;
import net.serlith.sakurabans.core.storage.repository.ImportRepository;
import net.serlith.sakurabans.core.sync.SyncPacket;
import net.serlith.sakurabans.core.sync.SyncService;
import net.serlith.sakurabans.core.util.Futures;
import org.jspecify.annotations.Nullable;

public final class ImportJobs {

    static final String LEASE = "import";
    static final Duration LEASE_TTL = Duration.ofMinutes(5);
    static final String SOURCE_PREFIX = "import:";

    private final ImportSources sources;
    private final ImportRunner runner;
    private final SelfImport self;
    private final ImportRepository repository;
    private final Lease lease;
    private final ConfigService config;
    private final SyncService sync;
    private final SyncReceiver receiver;
    private final Executor worker;
    private final InstantSource clock;
    private final ComponentLogger logger;
    private final AtomicBoolean running = new AtomicBoolean();

    public ImportJobs(final ImportSources sources, final ImportRunner runner,
            final SelfImport self, final ImportRepository repository, final Lease lease,
            final ConfigService config, final SyncService sync, final SyncReceiver receiver,
            final Executor worker, final InstantSource clock, final ComponentLogger logger) {
        this.sources = sources;
        this.runner = runner;
        this.self = self;
        this.repository = repository;
        this.lease = lease;
        this.config = config;
        this.sync = sync;
        this.receiver = receiver;
        this.worker = worker;
        this.clock = clock;
        this.logger = logger;
    }

    public List<String> ids() {
        return this.sources.ids();
    }

    public CompletableFuture<ImportOutcome> survey(final String id) {
        return CompletableFuture.supplyAsync(() -> this.withSource(id, ImportOutcome.Problem
                .UNREADABLE, (source, context) -> {
                    if (source == null) {
                        final Optional<ImportOutcome.Refused> refused =
                                this.self.check(context.database());
                        if (refused.isPresent()) {
                            return refused.get();
                        }
                        return new ImportOutcome.Surveyed(this.self.survey(context.database()));
                    }
                    return new ImportOutcome.Surveyed(source.survey(context));
                }), this.worker);
    }

    public CompletableFuture<ImportOutcome> run(final String id, final boolean dryRun,
            final Actor actor) {
        final ImportOptions options = ImportOptions.builder()
                .dryRun(dryRun)
                .enforcement(Enforcement.GLOBAL)
                .source(SOURCE_PREFIX + id)
                .build();
        return CompletableFuture.supplyAsync(() -> this.exclusive(() -> {
            this.logger.info("Import from {} started by {}{}", id, actor.operator(),
                    dryRun ? " (dry run)" : "");
            return this.withSource(id, ImportOutcome.Problem.FAILED, (source, context) -> {
                final SakuraImportReport report;
                if (source == null) {
                    final Optional<ImportOutcome.Refused> refused =
                            this.self.check(context.database());
                    if (refused.isPresent()) {
                        return refused.get();
                    }
                    report = this.self.run(context.database(), dryRun, options.batchSize());
                } else {
                    report = this.runner.run(source, context, options, this::renewLease);
                }
                return this.finish(report, options, actor, id);
            });
        }), this.worker);
    }

    public CompletableFuture<ImportOutcome> run(final Iterable<ImportedPunishment> punishments,
            final ImportOptions options, final Actor actor) {
        return CompletableFuture.supplyAsync(() -> this.exclusive(() -> this.finish(
                this.runner.run(punishments, options, this::renewLease), options, actor,
                options.source())), this.worker);
    }

    private ImportOutcome exclusive(final Supplier<ImportOutcome> work) {
        if (!this.running.compareAndSet(false, true)) {
            return new ImportOutcome.Busy();
        }
        try {
            if (!this.lease.tryAcquire(LEASE, LEASE_TTL).join()) {
                return new ImportOutcome.Busy();
            }
            try {
                return work.get();
            } finally {
                // Released before the next import here may take it again.
                Futures.logged(this.lease.release(LEASE), this.logger,
                        "release the import lease").handle((ignored, failure) -> null).join();
            }
        } finally {
            this.running.set(false);
        }
    }

    private void renewLease() {
        if (!this.lease.tryAcquire(LEASE, LEASE_TTL).join()) {
            throw new IllegalStateException("another instance took over the import lease");
        }
    }

    @FunctionalInterface
    private interface SourceWork {
        // source is null for the self import.
        ImportOutcome run(@Nullable ImportSource source, SourceContext context)
                throws Exception;
    }

    private ImportOutcome withSource(final String id, final ImportOutcome.Problem failure,
            final SourceWork work) {
        if (!this.sources.known(id)) {
            return new ImportOutcome.Refused(ImportOutcome.Problem.UNKNOWN_SOURCE, id);
        }
        final ImportSettings settings;
        try {
            settings = this.config.imports();
        } catch (final ConfigException e) {
            return new ImportOutcome.Refused(ImportOutcome.Problem.CONFIG_INVALID,
                    String.join("; ", e.errors().stream().map(ConfigIssue::toString).toList()));
        }
        final ImportSource source = this.sources.source(id).orElse(null);
        final Optional<String> section =
                source == null ? Optional.of(ImportSources.SELF) : source.databaseSection();
        SourceDatabase database = null;
        if (section.isPresent()) {
            final ImportSettings.Database connection =
                    settings.database(section.get()).orElse(null);
            if (connection == null || connection.jdbcUrl().isBlank()) {
                return new ImportOutcome.Refused(ImportOutcome.Problem.NOT_CONFIGURED,
                        section.get());
            }
            try {
                database = SourceDatabase.open(connection);
            } catch (final SQLException | RuntimeException e) {
                this.logger.warn("Can't open the {} database {}", id, connection, e);
                return new ImportOutcome.Refused(ImportOutcome.Problem.UNREACHABLE,
                        describe(e));
            }
        }
        try (SourceDatabase open = database) {
            return work.run(source, new SourceContext(id, settings, open));
        } catch (final Exception e) {
            final Throwable cause = Futures.unwrap(e);
            this.logger.error("Import from {} failed", id, cause);
            return new ImportOutcome.Refused(failure, describe(cause));
        }
    }

    private ImportOutcome finish(final SakuraImportReport report, final ImportOptions options,
            final Actor actor, final String label) {
        this.logger.info("Import from {} done{}: {} imported, {} already there, {} failed,"
                        + " {} players, {} superseded, {} texts shortened", label,
                report.dryRun() ? " (dry run)" : "", report.imported(), report.skipped(),
                report.failed(), report.players(), report.superseded(), report.truncated());
        report.failures().forEach(failure -> this.logger.warn("Not imported: {} {}",
                failure.externalId().orElse("#" + failure.index()), failure.errors()));
        if (report.dryRun()) {
            return new ImportOutcome.Done(report);
        }
        this.repository.audit(this.clock.instant(), actor, label, summary(report)).join();
        if (report.imported() > 0 || report.players() > 0 || !report.tables().isEmpty()) {
            this.sync.publish(new SyncPacket.ImportedPacket(
                    options.enforcement() == Enforcement.GLOBAL));
            // Logged by the receiver; the import itself is done either way.
            this.receiver.imported(options.enforcement() != Enforcement.NONE)
                    .handle((ignored, failure) -> null).join();
        }
        return new ImportOutcome.Done(report);
    }

    static String summary(final SakuraImportReport report) {
        return "imported=" + report.imported() + " skipped=" + report.skipped() + " failed="
                + report.failed() + " players=" + report.players() + " superseded="
                + report.superseded() + " truncated=" + report.truncated();
    }

    private static String describe(final Throwable failure) {
        final String message = failure.getMessage();
        return message == null || message.isBlank()
                ? failure.getClass().getSimpleName()
                : message.strip();
    }
}
