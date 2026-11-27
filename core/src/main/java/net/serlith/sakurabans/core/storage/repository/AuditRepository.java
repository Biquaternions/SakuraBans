package net.serlith.sakurabans.core.storage.repository;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import net.serlith.sakurabans.api.model.Actor;
import net.serlith.sakurabans.api.model.Page;
import net.serlith.sakurabans.core.model.AuditAction;
import net.serlith.sakurabans.core.model.AuditEntry;
import net.serlith.sakurabans.core.model.AuditFilter;
import net.serlith.sakurabans.core.model.SakuraPage;
import net.serlith.sakurabans.core.model.SubjectType;
import net.serlith.sakurabans.core.storage.StorageService;
import net.serlith.sakurabans.core.storage.dbo.OperatorKind;
import net.serlith.sakurabans.core.storage.dbo.SakuraTables;
import net.serlith.sakurabans.core.storage.dbo.records.SbAuditLogRecord;
import net.serlith.sakurabans.core.storage.dbo.tables.SbAuditLog;
import org.jooq.Condition;
import org.jooq.DSLContext;
import org.jooq.impl.DSL;
import org.jspecify.annotations.Nullable;

public final class AuditRepository {

    private static final char ESCAPE = '!';

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

    public CompletableFuture<Page<AuditEntry>> search(final AuditFilter filter, final int page,
            final int pageSize) {
        final SbAuditLog log = SakuraTables.AUDIT_LOG;
        final Condition where = condition(filter);
        return this.storage.read(dsl -> {
            final long counted = dsl.fetchCount(log, where);
            final long offset = (long) (page - 1) * pageSize;
            final List<AuditEntry> items = dsl.selectFrom(log).where(where)
                    .orderBy(log.AT.desc(), log.ID.desc())
                    .limit(pageSize).offset(offset)
                    .fetch(AuditRepository::entry);
            // As in PunishmentRepository.query: an entry written between the two statements.
            final long total = items.isEmpty()
                    ? counted : Math.max(counted, offset + items.size());
            return (Page<AuditEntry>) new SakuraPage<>(items, total, page, pageSize);
        });
    }

    public CompletableFuture<List<AuditEntry>> batch(final AuditFilter filter,
            final @Nullable AuditEntry after, final int limit) {
        final SbAuditLog log = SakuraTables.AUDIT_LOG;
        Condition where = condition(filter);
        if (after != null) {
            where = where.and(log.AT.lt(after.at())
                    .or(log.AT.eq(after.at()).and(log.ID.lt(after.id()))));
        }
        final Condition bounded = where;
        return this.storage.read(dsl -> dsl.selectFrom(log).where(bounded)
                .orderBy(log.AT.desc(), log.ID.desc()).limit(limit)
                .fetch(AuditRepository::entry));
    }

    public CompletableFuture<List<AuditEntry>> recentChanges(final List<SubjectType> types,
            final int limit) {
        final SbAuditLog log = SakuraTables.AUDIT_LOG;
        return this.storage.read(dsl -> {
            final List<AuditEntry> all = new ArrayList<>();
            for (final SubjectType type : types) {
                all.addAll(dsl.selectFrom(log).where(log.SUBJECT_TYPE.eq(type))
                        .orderBy(log.AT.desc(), log.ID.desc()).limit(limit)
                        .fetch(AuditRepository::entry));
            }
            all.sort(Comparator.comparing(AuditEntry::at).thenComparing(AuditEntry::id)
                    .reversed());
            return List.copyOf(all.subList(0, Math.min(limit, all.size())));
        });
    }

    public CompletableFuture<Integer> prune(final Instant before, final int limit) {
        final SbAuditLog log = SakuraTables.AUDIT_LOG;
        return this.storage.write(configuration -> {
            final DSLContext dsl = configuration.dsl();
            final List<Long> ids = dsl.select(log.ID).from(log).where(log.AT.lt(before))
                    .orderBy(log.AT.asc()).limit(limit).fetch(log.ID);
            return ids.isEmpty() ? 0
                    : dsl.deleteFrom(log).where(log.ID.in(ids)).execute();
        });
    }

    private static Condition condition(final AuditFilter filter) {
        final SbAuditLog log = SakuraTables.AUDIT_LOG;
        Condition where = DSL.noCondition();
        if (filter.actorUuid() != null) {
            where = where.and(log.ACTOR_UUID.eq(filter.actorUuid()));
        }
        if (filter.consoleActor()) {
            where = where.and(log.ACTOR_UUID.isNull())
                    .and(log.ACTOR_KIND.eq(OperatorKind.CONSOLE));
        }
        if (filter.playerUuid() != null) {
            where = where.and(log.SUBJECT_TYPE.in(SubjectType.PLAYER, SubjectType.ALLOW_LIST))
                    .and(log.SUBJECT_ID.eq(filter.playerUuid().toString()));
        }
        if (filter.punishmentId() != null) {
            where = where.and(log.SUBJECT_TYPE.eq(SubjectType.PUNISHMENT))
                    .and(log.SUBJECT_ID.eq(Long.toString(filter.punishmentId())));
        }
        if (filter.template() != null) {
            // The subject is the name, or "<scope> <name>" for a variant.
            final String name = filter.template().strip().toLowerCase(Locale.ROOT);
            where = where.and(log.SUBJECT_TYPE.eq(SubjectType.TEMPLATE))
                    .and(log.SUBJECT_ID.eq(name)
                            .or(log.SUBJECT_ID.like("% " + escape(name), ESCAPE)));
        }
        if (filter.setting() != null) {
            // A settings entry's values are "{key=value, key=value}"; secrets are masked there.
            final String key = escape(filter.setting().strip());
            where = where.and(log.SUBJECT_TYPE.eq(SubjectType.SETTINGS))
                    .and(log.NEW_VALUE.like("%{" + key + "=%", ESCAPE)
                            .or(log.NEW_VALUE.like("%, " + key + "=%", ESCAPE)));
        }
        if (filter.action() != null) {
            where = where.and(log.ACTION.eq(filter.action()));
        }
        if (filter.since() != null) {
            where = where.and(log.AT.ge(filter.since()));
        }
        return where;
    }

    private static String escape(final String text) {
        return text.replace("!", "!!").replace("%", "!%").replace("_", "!_");
    }

    static AuditEntry entry(final SbAuditLogRecord row) {
        return new AuditEntry(row.id(), row.at(), StoredOperator.actor(row.actorKind(),
                row.actorUuid(), row.actorName(), row.source()), row.action(),
                row.subjectType(), row.subjectId(), row.oldValue(), row.newValue());
    }
}
