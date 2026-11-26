package net.serlith.sakurabans.core.importer;

import java.time.InstantSource;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.kyori.adventure.text.logger.slf4j.ComponentLogger;
import net.serlith.sakurabans.api.model.Actor;
import net.serlith.sakurabans.api.model.EndKind;
import net.serlith.sakurabans.api.model.ErasedAddressVictim;
import net.serlith.sakurabans.api.model.Lengths;
import net.serlith.sakurabans.api.model.Operator;
import net.serlith.sakurabans.api.model.PunishmentType;
import net.serlith.sakurabans.api.validation.FieldError;
import net.serlith.sakurabans.api.validation.FieldError.Code;
import net.serlith.sakurabans.api.validation.Validator;
import net.serlith.sakurabans.core.model.ImportedPlayer;
import net.serlith.sakurabans.core.storage.repository.ImportRepository;
import net.serlith.sakurabans.core.storage.repository.ImportRow;
import net.serlith.sakurabans.core.storage.repository.NewPunishment;
import org.jspecify.annotations.Nullable;

public final class ImportRunner {

    static final int MAX_FAILURES = 50;
    static final int BATCH_SIZE = 500;

    private final ImportRepository repository;
    private final InstantSource clock;
    private final ComponentLogger logger;

    public ImportRunner(final ImportRepository repository, final InstantSource clock,
            final ComponentLogger logger) {
        this.repository = repository;
        this.clock = clock;
        this.logger = logger;
    }

    public ImportReport run(final ImportSource source, final SourceContext context,
            final boolean dryRun, final Runnable afterBatch) throws Exception {
        final Batch batch = new Batch(source.id(), dryRun, afterBatch);
        source.read(context, batch);
        return batch.finish();
    }

    static List<FieldError> problems(final ImportedPunishment punishment) {
        final ImportedPunishment.End end = punishment.end();
        return Validator.create()
                .check(!(punishment.victim() instanceof ErasedAddressVictim), "victim",
                        Code.NOT_ALLOWED, "an erased address can't be imported")
                .check(!punishment.shadow() || punishment.type() == PunishmentType.MUTE,
                        "shadow", Code.NOT_ALLOWED, "only mutes can be shadow mutes")
                .maxLength("reason", punishment.reason(), Lengths.MAX_REASON)
                .maxLength("endReason", end == null ? null : end.reason(), Lengths.MAX_REASON)
                .maxLength("originServer", punishment.originServer(), Lengths.MAX_IDENTIFIER)
                .name("externalId", punishment.externalId(), ImportedPunishment.MAX_EXTERNAL_ID)
                .check(punishment.expiresAt() == null
                                || punishment.expiresAt().isAfter(punishment.createdAt()),
                        "expiresAt", Code.WRONG_ORDER, "must be after createdAt")
                .check(end == null || !end.at().isBefore(punishment.createdAt()), "endedAt",
                        Code.WRONG_ORDER, "must not be before createdAt")
                .check(!punishment.type().instant()
                                || punishment.expiresAt() == null && end == null, "type",
                        Code.NOT_ALLOWED, "a kick can't expire or end")
                .errors();
    }

    private final class Batch implements SourceReader {

        private final String label;
        private final String source;
        private final boolean dryRun;
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

        Batch(final String label, final boolean dryRun, final Runnable afterBatch) {
            this.label = label;
            this.source = ImportJobs.SOURCE_PREFIX + label;
            this.dryRun = dryRun;
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
        public void punishment(final ImportedPunishment punishment) {
            final long position = this.index++;
            final List<FieldError> problems = problems(punishment);
            if (!problems.isEmpty()) {
                this.fail(position, punishment.externalId(), problems);
                return;
            }
            final ImportedPunishment.End end = punishment.end();
            final String endReason = end == null || end.reason() == null ? null
                    : ImportValues.plain(end.reason());
            this.rows.add(new ImportRow(new NewPunishment(punishment.type(),
                    punishment.shadow(), punishment.victim(),
                    punishment.scope(), ImportValues.plain(punishment.reason()),
                    Actor.of(punishment.operator(), this.source),
                    punishment.createdAt(), punishment.expiresAt(),
                    punishment.originServer(), punishment.visibility(), null, null,
                    List.of(), punishment.externalId()),
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
                this.failures.add(new ImportReport.Failure(position, externalId, errors));
            }
        }

        private void flushIfFull() {
            if (this.players.size() + this.rows.size() >= BATCH_SIZE) {
                this.flush();
            }
        }

        private void flush() {
            if (this.players.isEmpty() && this.rows.isEmpty()) {
                return;
            }
            if (this.dryRun) {
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
                    this.dryRun ? " (dry run)" : "");
            this.afterBatch.run();
        }

        ImportReport finish() {
            this.flush();
            return new ImportReport(this.dryRun, this.imported, this.skipped,
                    this.failures, this.failed, this.playerCount, this.superseded,
                    this.truncated, Map.of());
        }
    }

    private static @Nullable Operator endedBy(final ImportedPunishment.End end) {
        final boolean needsActor = end.kind() == EndKind.REVOKED || end.kind() == EndKind.VOIDED;
        return end.operator() != null ? end.operator()
                : needsActor ? ImportValues.UNKNOWN_REVOKER : null;
    }

}
