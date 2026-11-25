package net.serlith.sakurabans.core.storage.repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import net.serlith.sakurabans.api.audit.AuditAction;
import net.serlith.sakurabans.api.audit.SubjectType;
import net.serlith.sakurabans.api.model.Actor;
import net.serlith.sakurabans.api.wave.BanWave;
import net.serlith.sakurabans.core.model.SakuraBanWave;
import net.serlith.sakurabans.core.storage.StorageService;
import net.serlith.sakurabans.core.storage.dbo.SakuraTables;
import net.serlith.sakurabans.core.storage.dbo.records.SbBanWaveEntriesRecord;
import net.serlith.sakurabans.core.storage.dbo.records.SbBanWavesRecord;
import net.serlith.sakurabans.core.storage.dbo.tables.SbBanWaveEntries;
import net.serlith.sakurabans.core.storage.dbo.tables.SbBanWaves;
import org.jooq.Condition;
import org.jooq.DSLContext;
import org.jooq.impl.DSL;
import org.jspecify.annotations.Nullable;

public final class BanWaveRepository {

    private final StorageService storage;

    public BanWaveRepository(final StorageService storage) {
        this.storage = storage;
    }

    public record StoredEntry(long id, long waveId, String draft, Actor addedBy, Instant addedAt,
            @Nullable String result, @Nullable Long punishmentId) {
    }

    public sealed interface Change {

        record NotFound() implements Change {
        }

        record Conflict(long currentVersion) implements Change {
        }

        record WrongStatus(BanWave.Status status) implements Change {
        }

        record Done(BanWave wave, long entryId) implements Change {
        }
    }

    public CompletableFuture<List<BanWave>> waves(final Set<BanWave.Status> statuses) {
        final SbBanWaves waves = SakuraTables.BAN_WAVES;
        return this.storage.read(dsl -> dsl.selectFrom(waves)
                .where(statuses.isEmpty() ? DSL.noCondition() : waves.STATUS.in(statuses))
                .orderBy(waves.ID.desc())
                .fetch(row -> toWave(dsl, row)));
    }

    public CompletableFuture<Optional<BanWave>> wave(final long id) {
        return this.storage.read(dsl -> find(dsl, id));
    }

    public CompletableFuture<List<StoredEntry>> entries(final long waveId) {
        final SbBanWaveEntries entries = SakuraTables.BAN_WAVE_ENTRIES;
        return this.storage.read(dsl -> dsl.selectFrom(entries)
                .where(entries.WAVE_ID.eq(waveId))
                .orderBy(entries.ID.asc())
                .fetch(BanWaveRepository::entry));
    }

    public CompletableFuture<BanWave> create(final String name, final Actor actor,
            final Instant at) {
        final SbBanWaves waves = SakuraTables.BAN_WAVES;
        return this.storage.write(configuration -> {
            final DSLContext dsl = configuration.dsl();
            final StoredOperator by = StoredOperator.of(actor.operator());
            final long id = dsl.insertInto(waves)
                    .set(waves.NAME, name)
                    .set(waves.STATUS, BanWave.Status.DRAFT)
                    .set(waves.CREATED_BY_KIND, by.kind())
                    .set(waves.CREATED_BY_UUID, by.uuid())
                    .set(waves.CREATED_BY_NAME, by.name())
                    .set(waves.CREATED_BY_SOURCE, actor.source())
                    .set(waves.CREATED_AT, at)
                    .set(waves.VERSION, 1L)
                    .returningResult(waves.ID)
                    .fetchSingle()
                    .value1();
            AuditRepository.append(dsl, at, actor, AuditAction.CREATE, SubjectType.BAN_WAVE,
                    Long.toString(id), null, name);
            return find(dsl, id).orElseThrow();
        });
    }

    public CompletableFuture<Change> addEntry(final long waveId, final String draft,
            final String summary, final Actor actor, final Instant at) {
        final SbBanWaveEntries entries = SakuraTables.BAN_WAVE_ENTRIES;
        return this.storage.write(configuration -> {
            final DSLContext dsl = configuration.dsl();
            final Change editable = editable(dsl, waveId);
            if (editable != null) {
                return editable;
            }
            final StoredOperator by = StoredOperator.of(actor.operator());
            final long id = dsl.insertInto(entries)
                    .set(entries.WAVE_ID, waveId)
                    .set(entries.DRAFT, draft)
                    .set(entries.ADDED_BY_KIND, by.kind())
                    .set(entries.ADDED_BY_UUID, by.uuid())
                    .set(entries.ADDED_BY_NAME, by.name())
                    .set(entries.ADDED_BY_SOURCE, actor.source())
                    .set(entries.ADDED_AT, at)
                    .returningResult(entries.ID)
                    .fetchSingle()
                    .value1();
            bump(dsl, waveId);
            AuditRepository.append(dsl, at, actor, AuditAction.UPDATE, SubjectType.BAN_WAVE,
                    Long.toString(waveId), null, "+ entry " + id + ": " + summary);
            return new Change.Done(find(dsl, waveId).orElseThrow(), id);
        });
    }

    public CompletableFuture<Change> removeEntry(final long waveId, final long entryId,
            final Actor actor, final Instant at) {
        final SbBanWaveEntries entries = SakuraTables.BAN_WAVE_ENTRIES;
        return this.storage.write(configuration -> {
            final DSLContext dsl = configuration.dsl();
            final Change editable = editable(dsl, waveId);
            if (editable != null) {
                return editable;
            }
            if (dsl.deleteFrom(entries).where(entries.ID.eq(entryId))
                    .and(entries.WAVE_ID.eq(waveId)).execute() == 0) {
                return new Change.NotFound();
            }
            bump(dsl, waveId);
            AuditRepository.append(dsl, at, actor, AuditAction.UPDATE, SubjectType.BAN_WAVE,
                    Long.toString(waveId), "entry " + entryId, null);
            return new Change.Done(find(dsl, waveId).orElseThrow(), entryId);
        });
    }

    public CompletableFuture<Change> transition(final long waveId,
            final Set<BanWave.Status> from, final BanWave.Status to,
            final @Nullable Instant scheduledAt, final @Nullable Long expectedVersion,
            final Actor actor, final AuditAction action, final Instant at) {
        final SbBanWaves waves = SakuraTables.BAN_WAVES;
        return this.storage.write(configuration -> {
            final DSLContext dsl = configuration.dsl();
            final SbBanWavesRecord row = dsl.selectFrom(waves).where(waves.ID.eq(waveId))
                    .forUpdate().fetchOne();
            if (row == null) {
                return new Change.NotFound();
            }
            if (expectedVersion != null && row.version() != expectedVersion.longValue()) {
                return new Change.Conflict(row.version());
            }
            if (!from.contains(row.status())) {
                return new Change.WrongStatus(row.status());
            }
            dsl.update(waves)
                    .set(waves.STATUS, to)
                    .set(waves.SCHEDULED_AT, to == BanWave.Status.SCHEDULED ? scheduledAt
                            : row.scheduledAt())
                    .set(waves.RELEASED_AT, to == BanWave.Status.RELEASING
                            || to == BanWave.Status.RELEASED ? at : row.releasedAt())
                    .set(waves.VERSION, waves.VERSION.plus(1L))
                    .where(waves.ID.eq(waveId))
                    .execute();
            AuditRepository.append(dsl, at, actor, action, SubjectType.BAN_WAVE,
                    Long.toString(waveId), row.status().name(), to.name()
                            + (scheduledAt == null ? "" : " " + scheduledAt));
            return new Change.Done(find(dsl, waveId).orElseThrow(), 0);
        });
    }

    public CompletableFuture<Void> result(final long entryId, final String result,
            final @Nullable Long punishmentId) {
        final SbBanWaveEntries entries = SakuraTables.BAN_WAVE_ENTRIES;
        return this.storage.write(configuration -> {
            configuration.dsl().update(entries)
                    .set(entries.RESULT, result)
                    .set(entries.PUNISHMENT_ID, punishmentId)
                    .where(entries.ID.eq(entryId))
                    .execute();
            return null;
        });
    }

    public CompletableFuture<List<BanWave>> due(final Instant now, final Instant stuck) {
        final SbBanWaves waves = SakuraTables.BAN_WAVES;
        final Condition due = waves.STATUS.eq(BanWave.Status.SCHEDULED)
                .and(waves.SCHEDULED_AT.le(now))
                .or(waves.STATUS.eq(BanWave.Status.RELEASING)
                        .and(waves.RELEASED_AT.le(stuck)));
        return this.storage.read(dsl -> dsl.selectFrom(waves).where(due)
                .orderBy(waves.ID.asc()).fetch(row -> toWave(dsl, row)));
    }

    private static @Nullable Change editable(final DSLContext dsl, final long waveId) {
        final SbBanWaves waves = SakuraTables.BAN_WAVES;
        final SbBanWavesRecord row = dsl.selectFrom(waves).where(waves.ID.eq(waveId))
                .forUpdate().fetchOne();
        if (row == null) {
            return new Change.NotFound();
        }
        if (row.status() != BanWave.Status.DRAFT && row.status() != BanWave.Status.SCHEDULED) {
            return new Change.WrongStatus(row.status());
        }
        return null;
    }

    private static void bump(final DSLContext dsl, final long waveId) {
        final SbBanWaves waves = SakuraTables.BAN_WAVES;
        dsl.update(waves).set(waves.VERSION, waves.VERSION.plus(1L)).where(waves.ID.eq(waveId))
                .execute();
    }

    private static Optional<BanWave> find(final DSLContext dsl, final long id) {
        final SbBanWaves waves = SakuraTables.BAN_WAVES;
        return dsl.selectFrom(waves).where(waves.ID.eq(id)).fetchOptional()
                .map(row -> toWave(dsl, row));
    }

    private static BanWave toWave(final DSLContext dsl, final SbBanWavesRecord row) {
        final SbBanWaveEntries entries = SakuraTables.BAN_WAVE_ENTRIES;
        return new SakuraBanWave(row.id(), row.name(), row.status(), row.scheduledAt(),
                StoredOperator.actor(row.createdByKind(), row.createdByUuid(),
                        row.createdByName(), row.createdBySource()),
                row.createdAt(), row.status() == BanWave.Status.RELEASED ? row.releasedAt()
                        : null,
                dsl.fetchCount(entries, entries.WAVE_ID.eq(row.id())), row.version());
    }

    private static StoredEntry entry(final SbBanWaveEntriesRecord row) {
        return new StoredEntry(row.id(), row.waveId(), row.draft(), StoredOperator.actor(
                row.addedByKind(), row.addedByUuid(), row.addedByName(), row.addedBySource()),
                row.addedAt(), row.result(), row.punishmentId());
    }

}
