package net.serlith.sakurabans.core.storage.repository;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import net.serlith.sakurabans.api.audit.AuditAction;
import net.serlith.sakurabans.api.audit.AuditEntry;
import net.serlith.sakurabans.api.audit.AuditQuery;
import net.serlith.sakurabans.api.audit.SubjectType;
import net.serlith.sakurabans.api.model.Actor;
import net.serlith.sakurabans.api.model.Page;
import net.serlith.sakurabans.core.model.SakuraAuditEntry;
import net.serlith.sakurabans.core.model.SakuraPage;
import net.serlith.sakurabans.core.storage.StorageService;
import net.serlith.sakurabans.core.storage.dbo.SakuraTables;
import net.serlith.sakurabans.core.storage.dbo.records.SbAuditLogRecord;
import net.serlith.sakurabans.core.storage.dbo.tables.SbAuditLog;
import org.jooq.Condition;
import org.jooq.DSLContext;
import org.jspecify.annotations.Nullable;

public final class AuditRepository {

    private static final String CURSOR_KIND = "audit";

    private final StorageService storage;

    public AuditRepository(final StorageService storage) {
        this.storage = storage;
    }

    public CompletableFuture<Long> append(final Instant at, final Actor actor,
            final AuditAction action, final SubjectType subjectType, final String subjectId,
            final @Nullable String oldValue, final @Nullable String newValue) {
        return this.storage.write(configuration -> append(configuration.dsl(), at, actor, action,
                subjectType, subjectId, oldValue, newValue));
    }

    static long append(final DSLContext dsl, final Instant at, final Actor actor,
            final AuditAction action, final SubjectType subjectType, final String subjectId,
            final @Nullable String oldValue, final @Nullable String newValue) {
        final SbAuditLog log = SakuraTables.AUDIT_LOG;
        final StoredOperator operator = StoredOperator.of(actor.operator());
        return dsl.insertInto(log)
                .set(log.AT, at)
                .set(log.ACTOR_KIND, operator.kind())
                .set(log.ACTOR_UUID, operator.uuid())
                .set(log.ACTOR_NAME, operator.name())
                .set(log.SOURCE, actor.source())
                .set(log.ACTION, action)
                .set(log.SUBJECT_TYPE, subjectType)
                .set(log.SUBJECT_ID, subjectId)
                .set(log.OLD_VALUE, oldValue)
                .set(log.NEW_VALUE, newValue)
                .returningResult(log.ID)
                .fetchSingle()
                .value1();
    }

    public CompletableFuture<Page<AuditEntry>> query(final AuditQuery query) {
        final SbAuditLog log = SakuraTables.AUDIT_LOG;
        return this.storage.read(dsl -> {
            final List<Condition> conditions = conditions(query);
            final long total = dsl.fetchCount(log, conditions);
            final Cursor cursor = query.after()
                    .map(text -> Cursor.decode(text, CURSOR_KIND)).orElse(null);
            final List<Condition> paged = new ArrayList<>(conditions);
            int page = 1;
            if (cursor != null) {
                paged.add(log.ID.lt(cursor.id()));
                final long before = total - dsl.fetchCount(log, paged);
                page = (int) (before / query.pageSize()) + 1;
            }
            final List<SbAuditLogRecord> rows = dsl.selectFrom(log)
                    .where(paged)
                    .orderBy(log.ID.desc())
                    .limit(query.pageSize() + 1)
                    .fetch();
            final boolean more = rows.size() > query.pageSize();
            final List<AuditEntry> items = (more ? rows.subList(0, query.pageSize()) : rows)
                    .stream().map(AuditRepository::entry).toList();
            final String next = more
                    ? new Cursor(CURSOR_KIND, null, items.getLast().id()).encode()
                    : null;
            return new SakuraPage<>(items, total, page, query.pageSize(),
                    next);
        });
    }

    private static List<Condition> conditions(final AuditQuery query) {
        final SbAuditLog log = SakuraTables.AUDIT_LOG;
        final List<Condition> conditions = new ArrayList<>();
        query.subjectType().ifPresent(type -> conditions.add(log.SUBJECT_TYPE.eq(type)));
        query.subjectId().ifPresent(id -> conditions.add(log.SUBJECT_ID.eq(id)));
        query.actor().ifPresent(actor -> conditions.add(StoredOperator.identity(actor,
                log.ACTOR_KIND, log.ACTOR_UUID, log.ACTOR_NAME)));
        query.source().ifPresent(source -> conditions.add(log.SOURCE.eq(source)));
        if (!query.actions().isEmpty()) {
            conditions.add(log.ACTION.in(query.actions()));
        }
        query.from().ifPresent(from -> conditions.add(log.AT.ge(from)));
        query.to().ifPresent(to -> conditions.add(log.AT.lt(to)));
        return conditions;
    }

    private static AuditEntry entry(final SbAuditLogRecord row) {
        return new SakuraAuditEntry(row.id(), row.at(), StoredOperator.actor(row.actorKind(),
                row.actorUuid(), row.actorName(), row.source()), row.action(),
                row.subjectType(), row.subjectId(), row.oldValue(), row.newValue());
    }
}
