package net.serlith.sakurabans.core.importer;

import java.time.InstantSource;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.kyori.adventure.text.logger.slf4j.ComponentLogger;
import net.serlith.sakurabans.api.imports.ImportOptions;
import net.serlith.sakurabans.api.imports.ImportReport;
import net.serlith.sakurabans.api.imports.ImportedPunishment;
import net.serlith.sakurabans.api.model.Actor;
import net.serlith.sakurabans.api.model.EndKind;
import net.serlith.sakurabans.api.model.ErasedAddressVictim;
import net.serlith.sakurabans.api.model.Operator;
import net.serlith.sakurabans.api.validation.FieldError;
import net.serlith.sakurabans.api.validation.ValidationException;
import net.serlith.sakurabans.core.model.ApiValues;
import net.serlith.sakurabans.core.model.ImportedPlayer;
import net.serlith.sakurabans.core.model.SakuraImportReport;
import net.serlith.sakurabans.core.model.SakuraKind;
import net.serlith.sakurabans.core.punish.BuiltInKinds;
import net.serlith.sakurabans.core.punish.KindLookup;
import net.serlith.sakurabans.core.storage.repository.ImportRepository;
import net.serlith.sakurabans.core.storage.repository.ImportRow;
import net.serlith.sakurabans.core.storage.repository.NewPunishment;
import org.jspecify.annotations.Nullable;

public final class ImportRunner {

    static final int MAX_FAILURES = 50;

    private final ImportRepository repository;
    private final KindLookup kinds;
    private final InstantSource clock;
    private final ComponentLogger logger;

    public ImportRunner(final ImportRepository repository, final KindLookup kinds,
            final InstantSource clock, final ComponentLogger logger) {
        this.repository = repository;
        this.kinds = kinds;
        this.clock = clock;
        this.logger = logger;
    }

    public SakuraImportReport run(final ImportSource source, final SourceContext context,
            final ImportOptions options, final Runnable afterBatch) throws Exception {
        final Batch batch = new Batch(source.id(), options, afterBatch);
        source.read(context, batch);
        return batch.finish();
    }

    public SakuraImportReport run(final Iterable<ImportedPunishment> punishments,
            final ImportOptions options, final Runnable afterBatch) {
        final Batch batch = new Batch(options.source(), options, afterBatch);
        for (final ImportedPunishment punishment : punishments) {
            batch.punishment(punishment);
        }
        return batch.finish();
    }

    private SakuraKind kind(final ImportedPunishment punishment) {
        final String fallback = BuiltInKinds.defaultFor(punishment.type());
        return punishment.kind()
                .flatMap(this.kinds::find)
                .filter(kind -> kind.baseType() == punishment.type())
                // An unknown kind (or one of another type) becomes the type's own kind.
                .or(() -> this.kinds.find(fallback))
                .or(() -> BuiltInKinds.kind(fallback))
                .orElseThrow();
    }

    private final class Batch implements SourceReader {

        private final String label;
        private final ImportOptions options;
        private final Runnable afterBatch;
        private final List<ImportedPlayer> players = new ArrayList<>();
        private final List<ImportRow> rows = new ArrayList<>();
        private final List<ImportReport.Failure> failures = new ArrayList<>();
        private long index;
        private long imported;
        private long skipped;
        private long failed;
        private long playerCount;
        private long superseded;
        private long truncated;
        private int batches;

        Batch(final String label, final ImportOptions options, final Runnable afterBatch) {
            this.label = label;
            this.options = options;
            this.afterBatch = afterBatch;
        }

        @Override
        public void player(final ImportedPlayer player) {
            final String name = ImportValues.playerName(player.name()).orElse(null);
            if (name == null && player.address() == null) {
                this.fail(this.index, "player:" + player.uuid(), "name",
                        FieldError.Code.INVALID_FORMAT, "not a player name: " + player.name());
                return;
            }
            this.players.add(new ImportedPlayer(player.uuid(), name, player.address(),
                    player.firstSeen(), player.lastSeen()));
            this.flushIfFull();
        }

        @Override
        public void punishment(final ImportedPunishment given) {
            final long position = this.index++;
            final ImportedPunishment punishment;
            try {
                punishment = ApiValues.imported(given);
            } catch (final ValidationException e) {
                this.fail(position, given.externalId().orElse(null), e.errors());
                return;
            } catch (final RuntimeException e) {
                this.fail(position, given.externalId().orElse(null), "punishment",
                        FieldError.Code.INVALID, String.valueOf(e.getMessage()));
                return;
            }
            if (punishment.victim() instanceof ErasedAddressVictim) {
                this.fail(position, punishment.externalId().orElse(null), "victim",
                        FieldError.Code.NOT_ALLOWED, "an erased address can't be imported");
                return;
            }
            final SakuraKind kind = ImportRunner.this.kind(punishment);
            final ImportedPunishment.End end = punishment.end().orElse(null);
            final String endReason = end == null ? null
                    : end.reason().map(ImportValues::plain).orElse(null);
            this.rows.add(new ImportRow(new NewPunishment(punishment.type(), kind.id(),
                    kind.shadow(), kind.strictnessOrNull(), punishment.victim(),
                    punishment.scope(), ImportValues.plain(punishment.reason()),
                    Actor.of(punishment.operator(), this.options.source()),
                    punishment.createdAt(), punishment.expiresAt().orElse(null),
                    punishment.originServer().orElse(null), punishment.visibility(), null, null,
                    punishment.evidence(), punishment.externalId().orElse(null)),
                    end == null ? null : end.kind(), end == null ? null : end.at(),
                    end == null ? null : endedBy(end), endReason));
            this.flushIfFull();
        }

        @Override
        public void failure(final String table, final String id, final String problem) {
            this.fail(this.index++, this.label + ":" + table + ":" + id, "row",
                    FieldError.Code.INVALID, problem);
        }

        @Override
        public String fit(final String text, final int maxLength) {
            if (text.length() <= maxLength) {
                return text;
            }
            this.truncated++;
            return ImportValues.truncate(text, maxLength);
        }

        private void fail(final long position, final @Nullable String externalId,
                final String path, final FieldError.Code code, final String message) {
            this.fail(position, externalId, List.of(FieldError.of(path, code, message)));
        }

        private void fail(final long position, final @Nullable String externalId,
                final List<FieldError> errors) {
            this.failed++;
            if (this.failures.size() < MAX_FAILURES) {
                this.failures.add(new SakuraImportReport.Failure(position, externalId, errors));
            }
        }

        private void flushIfFull() {
            if (this.players.size() + this.rows.size() >= this.options.batchSize()) {
                this.flush();
            }
        }

        private void flush() {
            if (this.players.isEmpty() && this.rows.isEmpty()) {
                return;
            }
            if (this.options.dryRun()) {
                final List<String> ids = this.rows.stream()
                        .map(row -> row.punishment().externalId())
                        .filter(id -> id != null)
                        .toList();
                final Set<String> seen = new HashSet<>(
                        ImportRunner.this.repository.existing(ids).join());
                for (final ImportRow row : this.rows) {
                    final String id = row.punishment().externalId();
                    if (id == null || seen.add(id)) {
                        this.imported++;
                    } else {
                        this.skipped++;
                    }
                }
                this.playerCount += this.players.size();
            } else {
                final ImportRepository.BatchOutcome outcome = ImportRunner.this.repository
                        .write(this.players, this.rows, ImportRunner.this.clock.instant())
                        .join();
                this.imported += outcome.imported();
                this.skipped += outcome.skipped();
                this.superseded += outcome.superseded();
                this.playerCount += outcome.players();
                for (final ImportedPlayer unknown : outcome.unknownPlayers()) {
                    this.fail(this.index, "player:" + unknown.uuid(), "name",
                            FieldError.Code.BLANK, "only an address is known, no name");
                }
            }
            this.players.clear();
            this.rows.clear();
            this.batches++;
            ImportRunner.this.logger.info("Import from {}: batch {} done; {} imported, {}"
                            + " already there, {} failed, {} players so far{}", this.label,
                    this.batches, this.imported, this.skipped, this.failed, this.playerCount,
                    this.options.dryRun() ? " (dry run)" : "");
            this.afterBatch.run();
        }

        SakuraImportReport finish() {
            this.flush();
            return new SakuraImportReport(this.options.dryRun(), this.imported, this.skipped,
                    this.failures, this.failed, this.playerCount, this.superseded,
                    this.truncated, Map.of());
        }
    }

    private static @Nullable Operator endedBy(final ImportedPunishment.End end) {
        final boolean needsActor = end.kind() == EndKind.REVOKED || end.kind() == EndKind.VOIDED;
        return end.operator().orElse(needsActor ? ImportValues.UNKNOWN_REVOKER : null);
    }

}
