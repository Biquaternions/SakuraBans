package net.serlith.sakurabans.core.storage.repository;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import net.serlith.sakurabans.api.appeal.Appeal;
import net.serlith.sakurabans.api.appeal.AppealOutcome;
import net.serlith.sakurabans.api.appeal.AppealQuery;
import net.serlith.sakurabans.api.audit.AuditAction;
import net.serlith.sakurabans.api.audit.SubjectType;
import net.serlith.sakurabans.api.model.Actor;
import net.serlith.sakurabans.api.model.Page;
import net.serlith.sakurabans.core.model.SakuraAppeal;
import net.serlith.sakurabans.core.model.SakuraPage;
import net.serlith.sakurabans.core.storage.StorageService;
import net.serlith.sakurabans.core.storage.dbo.SakuraTables;
import net.serlith.sakurabans.core.storage.dbo.records.SbAppealsRecord;
import net.serlith.sakurabans.core.storage.dbo.tables.SbAppeals;
import org.jooq.Condition;
import org.jooq.DSLContext;
import org.jspecify.annotations.Nullable;

public final class AppealRepository {

    private static final String CURSOR_KIND = "appeals";

    private final StorageService storage;

    public AppealRepository(final StorageService storage) {
        this.storage = storage;
    }

    public sealed interface Change {

        record NotFound() implements Change {
        }

        record Conflict(long currentVersion) implements Change {
        }

        record AlreadyOpen() implements Change {
        }

        record NotOpen() implements Change {
        }

        record Done(Appeal appeal) implements Change {
        }
    }

    public CompletableFuture<Optional<Appeal>> appeal(final long id) {
        return this.storage.read(dsl -> find(dsl, id));
    }

    public CompletableFuture<Change> submit(final long punishmentId, final UUID appellant,
            final String message, final Actor actor, final Instant at) {
        final SbAppeals appeals = SakuraTables.APPEALS;
        return this.storage.write(configuration -> {
            final DSLContext dsl = configuration.dsl();
            // Locks the punishment, so two submissions can't both see "no open appeal".
            if (dsl.selectFrom(SakuraTables.PUNISHMENTS)
                    .where(SakuraTables.PUNISHMENTS.ID.eq(punishmentId)).forUpdate()
                    .fetchOne() == null) {
                return new Change.NotFound();
            }
            if (dsl.fetchExists(appeals, appeals.PUNISHMENT_ID.eq(punishmentId)
                    .and(appeals.STATUS.eq(Appeal.Status.OPEN)))) {
                return new Change.AlreadyOpen();
            }
            final long id = dsl.insertInto(appeals)
                    .set(appeals.PUNISHMENT_ID, punishmentId)
                    .set(appeals.APPELLANT, appellant)
                    .set(appeals.MESSAGE, message)
                    .set(appeals.CREATED_AT, at)
                    .set(appeals.STATUS, Appeal.Status.OPEN)
                    .set(appeals.VERSION, 1L)
                    .returningResult(appeals.ID)
                    .fetchSingle()
                    .value1();
            AuditRepository.append(dsl, at, actor, AuditAction.CREATE, SubjectType.APPEAL,
                    Long.toString(id), null, "#" + punishmentId + ": " + message);
            return new Change.Done(find(dsl, id).orElseThrow());
        });
    }

    public CompletableFuture<Change> decide(final long id, final AppealOutcome outcome,
            final @Nullable String note, final long expectedVersion, final Actor actor,
            final Instant at) {
        final SbAppeals appeals = SakuraTables.APPEALS;
        return this.storage.write(configuration -> {
            final DSLContext dsl = configuration.dsl();
            final SbAppealsRecord row = dsl.selectFrom(appeals).where(appeals.ID.eq(id))
                    .forUpdate().fetchOne();
            if (row == null) {
                return new Change.NotFound();
            }
            if (row.version() != expectedVersion) {
                return new Change.Conflict(row.version());
            }
            if (row.status() != Appeal.Status.OPEN) {
                return new Change.NotOpen();
            }
            final StoredOperator by = StoredOperator.of(actor.operator());
            dsl.update(appeals)
                    .set(appeals.STATUS, outcome == AppealOutcome.REJECT
                            ? Appeal.Status.REJECTED : Appeal.Status.ACCEPTED)
                    .set(appeals.OUTCOME, outcome)
                    .set(appeals.DECIDED_BY_KIND, by.kind())
                    .set(appeals.DECIDED_BY_UUID, by.uuid())
                    .set(appeals.DECIDED_BY_NAME, by.name())
                    .set(appeals.DECIDED_BY_SOURCE, actor.source())
                    .set(appeals.DECIDED_AT, at)
                    .set(appeals.NOTE, note)
                    .set(appeals.VERSION, appeals.VERSION.plus(1L))
                    .where(appeals.ID.eq(id))
                    .execute();
            AuditRepository.append(dsl, at, actor, AuditAction.DECIDE, SubjectType.APPEAL,
                    Long.toString(id), Appeal.Status.OPEN.name(), outcome.name()
                            + (note == null ? "" : ": " + note));
            return new Change.Done(find(dsl, id).orElseThrow());
        });
    }

    public CompletableFuture<Page<Appeal>> query(final AppealQuery query) {
        final SbAppeals appeals = SakuraTables.APPEALS;
        return this.storage.read(dsl -> {
            final List<Condition> conditions = new ArrayList<>();
            if (!query.statuses().isEmpty()) {
                conditions.add(appeals.STATUS.in(query.statuses()));
            }
            query.punishmentId().ifPresent(id -> conditions.add(appeals.PUNISHMENT_ID.eq(id)));
            query.appellant().ifPresent(uuid -> conditions.add(appeals.APPELLANT.eq(uuid)));
            final long total = dsl.fetchCount(appeals, conditions);
            final Cursor cursor = query.after()
                    .map(text -> Cursor.decode(text, CURSOR_KIND)).orElse(null);
            final List<Condition> paged = new ArrayList<>(conditions);
            int page = 1;
            if (cursor != null) {
                paged.add(appeals.ID.lt(cursor.id()));
                page = (int) ((total - dsl.fetchCount(appeals, paged)) / query.pageSize()) + 1;
            }
            final List<SbAppealsRecord> rows = dsl.selectFrom(appeals).where(paged)
                    .orderBy(appeals.ID.desc()).limit(query.pageSize() + 1).fetch();
            final boolean more = rows.size() > query.pageSize();
            final List<Appeal> items = (more ? rows.subList(0, query.pageSize()) : rows)
                    .stream().map(AppealRepository::toAppeal).toList();
            return new SakuraPage<>(items, total, page, query.pageSize(),
                    more ? new Cursor(CURSOR_KIND, null, items.getLast().id()).encode() : null);
        });
    }

    private static Optional<Appeal> find(final DSLContext dsl, final long id) {
        final SbAppeals appeals = SakuraTables.APPEALS;
        return dsl.selectFrom(appeals).where(appeals.ID.eq(id)).fetchOptional()
                .map(AppealRepository::toAppeal);
    }

    static Appeal toAppeal(final SbAppealsRecord row) {
        final SakuraAppeal.SakuraDecision decision = row.status() == Appeal.Status.OPEN
                || row.outcome() == null || row.decidedByKind() == null
                || row.decidedByName() == null || row.decidedBySource() == null
                || row.decidedAt() == null
                ? null
                : new SakuraAppeal.SakuraDecision(row.outcome(), StoredOperator.actor(
                        row.decidedByKind(), row.decidedByUuid(), row.decidedByName(),
                        row.decidedBySource()), row.decidedAt(), row.note());
        return new SakuraAppeal(row.id(), row.punishmentId(), row.appellant(), row.message(),
                row.createdAt(), row.status(), decision, row.version());
    }
}
