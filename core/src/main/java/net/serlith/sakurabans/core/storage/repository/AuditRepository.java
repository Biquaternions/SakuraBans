package net.serlith.sakurabans.core.storage.repository;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import net.serlith.sakurabans.api.model.Actor;
import net.serlith.sakurabans.core.model.AuditAction;
import net.serlith.sakurabans.core.model.AuditEntry;
import net.serlith.sakurabans.core.model.SubjectType;
import net.serlith.sakurabans.core.storage.StorageService;
import net.serlith.sakurabans.core.storage.dbo.SakuraTables;
import net.serlith.sakurabans.core.storage.dbo.records.SbAuditLogRecord;
import net.serlith.sakurabans.core.storage.dbo.tables.SbAuditLog;
import org.jooq.DSLContext;
import org.jspecify.annotations.Nullable;

public final class AuditRepository {

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

    public CompletableFuture<List<AuditEntry>> about(final SubjectType subjectType,
            final String subjectId, final int limit) {
        final SbAuditLog log = SakuraTables.AUDIT_LOG;
        return this.storage.read(dsl -> dsl.selectFrom(log)
                .where(log.SUBJECT_TYPE.eq(subjectType))
                .and(log.SUBJECT_ID.eq(subjectId))
                .orderBy(log.ID.desc())
                .limit(limit)
                .fetch(AuditRepository::entry));
    }

    static AuditEntry entry(final SbAuditLogRecord row) {
        return new AuditEntry(row.id(), row.at(), StoredOperator.actor(row.actorKind(),
                row.actorUuid(), row.actorName(), row.source()), row.action(),
                row.subjectType(), row.subjectId(), row.oldValue(), row.newValue());
    }
}
