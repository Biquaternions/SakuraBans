package net.serlith.sakurabans.core.importer;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.kyori.adventure.text.logger.slf4j.ComponentLogger;
import net.serlith.sakurabans.core.storage.dbo.SakuraTables;
import net.serlith.sakurabans.core.storage.repository.ImportRepository;

public final class SelfImport {

    private static final String PUNISHMENTS = "punishments";
    private static final String PLAYERS = "players";

    private final ImportRepository repository;
    private final ComponentLogger logger;

    public SelfImport(final ImportRepository repository, final ComponentLogger logger) {
        this.repository = repository;
        this.logger = logger;
    }

    public Optional<ImportOutcome.Refused> check(final SourceDatabase source) {
        final Optional<String> version =
                ImportRepository.sourceVersion(source.dsl(), source.prefix());
        if (version.isEmpty()) {
            return Optional.of(new ImportOutcome.Refused(ImportOutcome.Problem.NOT_SAKURABANS,
                    source.prefix()));
        }
        if (!version.get().strip().equals(Integer.toString(SakuraTables.SCHEMA_VERSION))) {
            return Optional.of(new ImportOutcome.Refused(ImportOutcome.Problem.OTHER_VERSION,
                    version.get().strip()));
        }
        final var occupied = this.repository.occupied().join();
        if (!occupied.isEmpty()) {
            return Optional.of(new ImportOutcome.Refused(ImportOutcome.Problem.TARGET_NOT_EMPTY,
                    String.join(", ", occupied)));
        }
        return Optional.empty();
    }

    public Map<String, Long> survey(final SourceDatabase source) {
        return ImportRepository.sourceCounts(source.dsl(), source.prefix());
    }

    public ImportReport run(final SourceDatabase source, final boolean dryRun,
            final int batchSize) {
        final Map<String, Long> tables;
        if (dryRun) {
            tables = this.survey(source);
        } else {
            final ImportRepository.Copied copied = this.repository.copy(source.dsl(),
                    source.prefix(), batchSize, source::stream,
                    line -> this.logger.info("Self import: copied {}", line)).join();
            tables = copied.tables();
            this.repository.resetIdentities().join();
            if (copied.rehashed() && copied.erasedKept() == 0) {
                this.logger.info("Self import: the source used another address-hash-key; its"
                        + " addresses were hashed again under this server's key");
            } else if (copied.rehashed()) {
                this.logger.warn("Self import: the source used another address-hash-key, so its"
                        + " addresses were hashed again under this server's key. {} erased"
                        + " address victims have no address to hash again: their punishments"
                        + " stay but no longer match an address (an erased composite still"
                        + " reaches its player). To keep them matching, import again into a"
                        + " new database with the source's address-hash-key in storage.yml.",
                        copied.erasedKept());
            }
        }
        return new ImportReport(dryRun, tables.getOrDefault(PUNISHMENTS, 0L), 0,
                List.of(), 0, tables.getOrDefault(PLAYERS, 0L), 0, 0, tables);
    }

}
