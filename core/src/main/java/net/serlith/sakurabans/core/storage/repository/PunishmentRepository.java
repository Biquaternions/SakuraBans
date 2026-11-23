package net.serlith.sakurabans.core.storage.repository;

import com.google.common.base.Preconditions;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import net.serlith.sakurabans.api.model.Actor;
import net.serlith.sakurabans.api.model.EndKind;
import net.serlith.sakurabans.api.model.EvidenceRef;
import net.serlith.sakurabans.api.model.Lengths;
import net.serlith.sakurabans.api.model.Page;
import net.serlith.sakurabans.api.model.Punishment;
import net.serlith.sakurabans.api.model.PunishmentType;
import net.serlith.sakurabans.api.model.Scope;
import net.serlith.sakurabans.api.model.Victim;
import net.serlith.sakurabans.api.model.Visibility;
import net.serlith.sakurabans.api.punish.OverridePolicy;
import net.serlith.sakurabans.api.punish.PunishmentQuery;
import net.serlith.sakurabans.api.punish.PunishmentQuery.Applicability;
import net.serlith.sakurabans.core.model.EndKinds;
import net.serlith.sakurabans.core.model.SakuraPage;
import net.serlith.sakurabans.core.storage.StorageService;
import net.serlith.sakurabans.core.storage.dbo.SakuraTables;
import net.serlith.sakurabans.core.storage.dbo.VictimKind;
import net.serlith.sakurabans.core.storage.dbo.records.SbActivePunishmentsRecord;
import net.serlith.sakurabans.core.storage.dbo.tables.SbActivePunishments;
import net.serlith.sakurabans.core.storage.dbo.tables.SbPunishments;
import net.serlith.sakurabans.core.storage.dbo.tables.SbVictims;
import org.jooq.Condition;
import org.jooq.DSLContext;
import org.jooq.Record;
import org.jooq.ResultQuery;
import org.jooq.SortField;
import org.jooq.impl.DSL;
import org.jspecify.annotations.Nullable;

public final class PunishmentRepository {

    private final StorageService storage;
    private final PunishmentMapper mapper;
    private final AltPolicy policy;

    public PunishmentRepository(final StorageService storage, final PunishmentMapper mapper) {
        this(storage, mapper, AltPolicy.NONE);
    }

    public PunishmentRepository(final StorageService storage, final PunishmentMapper mapper,
            final AltPolicy policy) {
        this.storage = storage;
        this.mapper = mapper;
        this.policy = policy;
    }

    public CompletableFuture<InsertOutcome> insert(final NewPunishment punishment,
            final OverridePolicy policy) {
        return this.storage.writeRetryingConflicts(configuration ->
                this.insert(configuration.dsl(), punishment, policy));
    }

    private InsertOutcome insert(final DSLContext dsl, final NewPunishment punishment,
            final OverridePolicy policy) {
        final SbActivePunishments active = SakuraTables.ACTIVE_PUNISHMENTS;
        final long victimId = VictimRepository.resolve(dsl, punishment.victim());
        final int scopeId = ScopeRepository.resolve(dsl, punishment.scope());
        final PunishmentType type = punishment.type();
        Punishment superseded = null;
        if (!type.instant() && !type.stacks()) {
            final SbActivePunishmentsRecord occupant = dsl.selectFrom(active)
                    .where(active.TYPE.eq(type))
                    .and(active.VICTIM_ID.eq(victimId))
                    .and(active.SCOPE_ID.eq(scopeId))
                    .forUpdate()
                    .fetchOne();
            if (occupant != null) {
                final Instant expiry = occupant.expiresAt();
                if (expiry != null && !expiry.isAfter(punishment.createdAt())) {
                    endInTransaction(dsl, occupant.punishmentId(), EndKind.EXPIRED, expiry,
                            null, null);
                } else if (policy == OverridePolicy.REJECT || policy
                        == OverridePolicy.REPLACE_IF_LONGER && !punishment.outlasts(expiry)) {
                    return new InsertOutcome.Rejected(
                            this.fetch(dsl, occupant.punishmentId()).orElseThrow());
                } else {
                    endInTransaction(dsl, occupant.punishmentId(), EndKind.SUPERSEDED,
                            punishment.createdAt(), null, null);
                    superseded = this.fetch(dsl, occupant.punishmentId()).orElseThrow();
                }
            }
        }
        final long id = insertRow(dsl, punishment, victimId, scopeId);
        if (!type.instant()) {
            dsl.insertInto(active)
                    .set(active.PUNISHMENT_ID, id)
                    .set(active.TYPE, type)
                    .set(active.VICTIM_ID, victimId)
                    .set(active.SCOPE_ID, scopeId)
                    .set(active.EXPIRES_AT, punishment.expiresAt())
                    // Warns stack: each takes its own slot; bans and mutes share slot 0.
                    .set(active.EXCLUSIVITY_SLOT, type.stacks() ? id : 0L)
                    .execute();
        }
        for (final EvidenceRef ref : punishment.evidence()) {
            final StoredOperator by = StoredOperator.of(punishment.actor().operator());
            dsl.insertInto(SakuraTables.PUNISHMENT_EVIDENCE)
                    .set(SakuraTables.PUNISHMENT_EVIDENCE.PUNISHMENT_ID, id)
                    .set(SakuraTables.PUNISHMENT_EVIDENCE.URI, ref.uri())
                    .set(SakuraTables.PUNISHMENT_EVIDENCE.ADDED_BY_KIND, by.kind())
                    .set(SakuraTables.PUNISHMENT_EVIDENCE.ADDED_BY_UUID, by.uuid())
                    .set(SakuraTables.PUNISHMENT_EVIDENCE.ADDED_BY_NAME, by.name())
                    .set(SakuraTables.PUNISHMENT_EVIDENCE.ADDED_BY_SOURCE,
                            punishment.actor().source())
                    .set(SakuraTables.PUNISHMENT_EVIDENCE.ADDED_AT, punishment.createdAt())
                    .execute();
        }
        return new InsertOutcome.Inserted(this.fetch(dsl, id).orElseThrow(), superseded);
    }

    private static long insertRow(final DSLContext dsl, final NewPunishment punishment,
            final long victimId, final int scopeId) {
        final SbPunishments p = SakuraTables.PUNISHMENTS;
        final StoredOperator operator = StoredOperator.of(punishment.actor().operator());
        return dsl.insertInto(p)
                .set(p.TYPE, punishment.type())
                .set(p.KIND, punishment.kind())
                .set(p.SHADOW, punishment.shadow())
                .set(p.STRICTNESS, punishment.strictness())
                .set(p.VICTIM_ID, victimId)
                .set(p.SCOPE_ID, scopeId)
                .set(p.REASON, punishment.reason())
                .set(p.OPERATOR_KIND, operator.kind())
                .set(p.OPERATOR_UUID, operator.uuid())
                .set(p.OPERATOR_NAME, operator.name())
                .set(p.SOURCE, punishment.actor().source())
                .set(p.CREATED_AT, punishment.createdAt())
                .set(p.EXPIRES_AT, punishment.expiresAt())
                .set(p.ORIGIN_SERVER, punishment.originServer())
                .set(p.VISIBILITY, punishment.visibility())
                .set(p.TEMPLATE_ID, punishment.templateId())
                .set(p.TEMPLATE_STEP, punishment.templateStep() == null ? null
                        : punishment.templateStep().shortValue())
                .set(p.VERSION, 1L)
                .set(p.EXTERNAL_ID, punishment.externalId())
                .returningResult(p.ID)
                .fetchSingle()
                .value1();
    }

    public CompletableFuture<Optional<Punishment>> byId(final long id) {
        return this.storage.read(dsl -> this.fetch(dsl, id));
    }

    public CompletableFuture<Optional<Punishment>> end(final long id, final EndKind kind,
            final Instant at, final @Nullable Actor actor, final @Nullable String reason) {
        Preconditions.checkNotNull(kind, "kind");
        Preconditions.checkNotNull(at, "at");
        Preconditions.checkArgument(actor != null || EndKinds.WITHOUT_ACTOR.contains(kind),
                "%s needs an actor", kind);
        Preconditions.checkArgument(reason == null || reason.length() <= Lengths.MAX_REASON,
                "reason is longer than %s characters", Lengths.MAX_REASON);
        return this.storage.write(configuration -> {
            final DSLContext dsl = configuration.dsl();
            return endInTransaction(dsl, id, kind, at, actor, reason)
                    ? this.fetch(dsl, id)
                    : Optional.<Punishment>empty();
        });
    }

    private static boolean endInTransaction(final DSLContext dsl, final long id,
            final EndKind kind, final Instant at, final @Nullable Actor actor,
            final @Nullable String reason) {
        final SbPunishments p = SakuraTables.PUNISHMENTS;
        final SbActivePunishments active = SakuraTables.ACTIVE_PUNISHMENTS;
        if (dsl.deleteFrom(active).where(active.PUNISHMENT_ID.eq(id)).execute() == 0) {
            return false;
        }
        final StoredOperator by = actor == null ? null : StoredOperator.of(actor.operator());
        dsl.update(p)
                .set(p.ENDED_AT, at)
                .set(p.END_KIND, kind)
                .set(p.REVOKER_KIND, by == null ? null : by.kind())
                .set(p.REVOKER_UUID, by == null ? null : by.uuid())
                .set(p.REVOKER_NAME, by == null ? null : by.name())
                .set(p.REVOKER_SOURCE, actor == null ? null : actor.source())
                .set(p.REVOKE_REASON, reason)
                .set(p.VERSION, p.VERSION.plus(1L))
                .where(p.ID.eq(id))
                .execute();
        return true;
    }

    public CompletableFuture<List<Punishment>> active(final PunishmentType type,
            final Victim victim, final @Nullable Scope scope, final Instant now) {
        final SbPunishments p = SakuraTables.PUNISHMENTS;
        final SbActivePunishments active = SakuraTables.ACTIVE_PUNISHMENTS;
        return this.storage.read(dsl -> this.mapper.map(dsl, PunishmentMapper.select(dsl)
                .join(active).on(active.PUNISHMENT_ID.eq(p.ID))
                .where(active.TYPE.eq(type))
                .and(StoredVictim.of(victim).matches(SakuraTables.VICTIMS))
                .and(scope == null ? DSL.noCondition() : ScopeRepository.matches(scope))
                .and(unexpired(now))
                .orderBy(p.CREATED_AT.desc(), p.ID.desc())
                .fetch()));
    }

    /** Every active, unexpired punishment on an address range (`ActiveRangeCache`, S30). */
    public CompletableFuture<List<Punishment>> activeRanges(final Instant now) {
        final SbPunishments p = SakuraTables.PUNISHMENTS;
        final SbActivePunishments active = SakuraTables.ACTIVE_PUNISHMENTS;
        return this.storage.read(dsl -> this.mapper.map(dsl, PunishmentMapper.select(dsl)
                .join(active).on(active.PUNISHMENT_ID.eq(p.ID))
                .where(SakuraTables.VICTIMS.KIND.eq(VictimKind.ADDRESS_RANGE))
                .and(unexpired(now))
                .fetch()));
    }

    /** Active rows whose expiry has passed (the sweeper ends them), oldest expiry first. */
    public CompletableFuture<List<Punishment>> expiredActive(final Instant now, final int limit) {
        final SbPunishments p = SakuraTables.PUNISHMENTS;
        final SbActivePunishments active = SakuraTables.ACTIVE_PUNISHMENTS;
        return this.storage.read(dsl -> this.mapper.map(dsl, PunishmentMapper.select(dsl)
                .join(active).on(active.PUNISHMENT_ID.eq(p.ID))
                .where(active.EXPIRES_AT.le(now))
                .orderBy(active.EXPIRES_AT.asc(), p.ID.asc())
                .limit(limit)
                .fetch()));
    }

    public CompletableFuture<List<Punishment>> activeApplicable(final PunishmentType type,
            final Applicability applicability, final Collection<? extends Scope> scopes,
            final Instant now) {
        return this.storage.read(dsl -> this.mapper.map(dsl, activeApplicableQuery(dsl, type,
                applicability, scopes, now, this.policy).fetch()));
    }

    /** The types of punishment active on these accounts (victims naming them), per account. */
    public CompletableFuture<Map<UUID, Set<PunishmentType>>> activeTypes(
            final Collection<UUID> uuids, final Instant now) {
        if (uuids.isEmpty()) {
            return CompletableFuture.completedFuture(Map.of());
        }
        final SbActivePunishments active = SakuraTables.ACTIVE_PUNISHMENTS;
        final SbVictims victims = SakuraTables.VICTIMS;
        return this.storage.read(dsl -> {
            final Map<UUID, Set<PunishmentType>> types = new HashMap<>();
            dsl.selectDistinct(victims.UUID, active.TYPE).from(active)
                    .join(victims).on(victims.ID.eq(active.VICTIM_ID))
                    .where(victims.UUID.in(uuids))
                    .and(victims.KIND.in(VictimKind.PLAYER, VictimKind.COMPOSITE))
                    .and(unexpired(now))
                    .fetch()
                    .forEach(row -> types.computeIfAbsent(row.value1(),
                            ignored -> EnumSet.noneOf(PunishmentType.class)).add(row.value2()));
            return Map.copyOf(types);
        });
    }

    /** Whether a punishment reaches this account and address (who to enforce it on). */
    public CompletableFuture<Boolean> reaches(final long punishmentId,
            final Applicability applicability, final Instant now) {
        final SbPunishments p = SakuraTables.PUNISHMENTS;
        return this.storage.read(dsl -> dsl.fetchExists(DSL.selectOne().from(p)
                .where(p.ID.eq(punishmentId))
                .and(p.VICTIM_ID.in(ApplicabilityConditions.victimIds(applicability,
                        this.policy, now)))));
    }

    static ResultQuery<Record> activeApplicableQuery(final DSLContext dsl,
            final PunishmentType type, final Applicability applicability,
            final Collection<? extends Scope> scopes, final Instant now,
            final AltPolicy policy) {
        final SbPunishments p = SakuraTables.PUNISHMENTS;
        final SbActivePunishments active = SakuraTables.ACTIVE_PUNISHMENTS;
        final Condition inScopes = DSL.or(scopes.stream().map(ScopeRepository::matches).toList());
        return PunishmentMapper.select(dsl)
                .join(active).on(active.PUNISHMENT_ID.eq(p.ID))
                .where(active.TYPE.eq(type))
                .and(active.SCOPE_ID.in(DSL.select(SakuraTables.SCOPES.ID)
                        .from(SakuraTables.SCOPES).where(inScopes)))
                .and(active.VICTIM_ID.in(ApplicabilityConditions.victimIds(applicability,
                        policy, now)))
                .and(unexpired(now))
                .orderBy(active.EXPIRES_AT.desc().nullsFirst(), p.ID.desc());
    }

    public CompletableFuture<Page<Punishment>> query(final PunishmentQuery query,
            final Instant now) {
        return this.storage.read(dsl -> {
            final List<Condition> conditions = conditions(query, now, this.policy);
            final long total = count(dsl, conditions);
            final Order order = Order.of(query.order());
            final Cursor cursor = query.after()
                    .map(text -> Cursor.decode(text, order.cursorKind())).orElse(null);
            final List<Condition> paged = new ArrayList<>(conditions);
            final int page;
            if (cursor == null) {
                page = query.page();
            } else {
                final Condition after = order.after(cursor);
                paged.add(after);
                final long before = total - count(dsl, append(conditions, after));
                page = (int) (before / query.pageSize()) + 1;
            }
            final List<Record> rows = PunishmentMapper.select(dsl)
                    .where(paged)
                    .orderBy(order.sort())
                    .limit(query.pageSize() + 1)
                    .offset(cursor == null ? (long) (query.page() - 1) * query.pageSize() : 0L)
                    .fetch();
            final boolean more = rows.size() > query.pageSize();
            final List<Punishment> items =
                    this.mapper.map(dsl, more ? rows.subList(0, query.pageSize()) : rows);
            final String next = more ? order.cursor(items.getLast()).encode() : null;
            return (Page<Punishment>) new SakuraPage<>(items, total, page, query.pageSize(),
                    next);
        });
    }

    public CompletableFuture<Long> count(final PunishmentQuery query, final Instant now) {
        return this.storage.read(dsl -> count(dsl, conditions(query, now, this.policy)));
    }

    private static long count(final DSLContext dsl, final List<Condition> conditions) {
        final SbPunishments p = SakuraTables.PUNISHMENTS;
        return dsl.selectCount()
                .from(p.join(SakuraTables.VICTIMS)
                        .on(SakuraTables.VICTIMS.ID.eq(p.VICTIM_ID))
                        .join(SakuraTables.SCOPES)
                        .on(SakuraTables.SCOPES.ID.eq(p.SCOPE_ID)))
                .where(conditions)
                .fetchSingle()
                .value1();
    }

    private static List<Condition> conditions(final PunishmentQuery query, final Instant now,
            final AltPolicy policy) {
        final SbPunishments p = SakuraTables.PUNISHMENTS;
        final SbActivePunishments active = SakuraTables.ACTIVE_PUNISHMENTS;
        final List<Condition> conditions = new ArrayList<>();
        if (!query.types().isEmpty()) {
            conditions.add(p.TYPE.in(query.types()));
        }
        if (!query.kinds().isEmpty()) {
            conditions.add(p.KIND.in(query.kinds()));
        }
        query.victim().ifPresent(victim ->
                conditions.add(StoredVictim.of(victim).matches(SakuraTables.VICTIMS)));
        query.applicableTo().ifPresent(applicability -> conditions.add(
                p.VICTIM_ID.in(ApplicabilityConditions.victimIds(applicability, policy, now))));
        query.operator().ifPresent(operator -> conditions.add(StoredOperator.identity(
                operator, p.OPERATOR_KIND, p.OPERATOR_UUID, p.OPERATOR_NAME)));
        query.revokedBy().ifPresent(operator -> conditions.add(p.END_KIND.eq(EndKind.REVOKED)
                .and(StoredOperator.identity(operator, p.REVOKER_KIND, p.REVOKER_UUID,
                        p.REVOKER_NAME))));
        query.scope().ifPresent(scope -> conditions.add(ScopeRepository.matches(scope)));
        if (query.activeOnly()) {
            conditions.add(p.ID.in(DSL.select(active.PUNISHMENT_ID).from(active)
                    .where(unexpired(now))));
        }
        if (!query.includeHidden()) {
            conditions.add(p.VISIBILITY.ne(Visibility.HIDDEN));
        }
        if (!query.includeVoided()) {
            conditions.add(p.VOIDED_AT.isNull());
        }
        query.createdAfter().ifPresent(after -> conditions.add(p.CREATED_AT.ge(after)));
        query.createdBefore().ifPresent(before -> conditions.add(p.CREATED_AT.lt(before)));
        return conditions;
    }

    private static Condition unexpired(final Instant now) {
        final SbActivePunishments active = SakuraTables.ACTIVE_PUNISHMENTS;
        return active.EXPIRES_AT.isNull().or(active.EXPIRES_AT.gt(now));
    }

    private static List<Condition> append(final List<Condition> conditions,
            final Condition condition) {
        final List<Condition> all = new ArrayList<>(conditions);
        all.add(condition);
        return all;
    }

    private Optional<Punishment> fetch(final DSLContext dsl, final long id) {
        final SbPunishments p = SakuraTables.PUNISHMENTS;
        final List<Punishment> found =
                this.mapper.map(dsl, PunishmentMapper.select(dsl).where(p.ID.eq(id)).fetch());
        return found.stream().findFirst();
    }

    private enum Order {
        NEWEST_FIRST, OLDEST_FIRST, EXPIRING_FIRST;

        static Order of(final PunishmentQuery.Order order) {
            return switch (order) {
                case NEWEST_FIRST -> NEWEST_FIRST;
                case OLDEST_FIRST -> OLDEST_FIRST;
                case EXPIRING_FIRST -> EXPIRING_FIRST;
            };
        }

        String cursorKind() {
            return "punishments." + this.name();
        }

        List<SortField<?>> sort() {
            final SbPunishments p = SakuraTables.PUNISHMENTS;
            return switch (this) {
                case NEWEST_FIRST -> List.of(p.CREATED_AT.desc(), p.ID.desc());
                case OLDEST_FIRST -> List.of(p.CREATED_AT.asc(), p.ID.asc());
                case EXPIRING_FIRST -> List.of(p.EXPIRES_AT.asc().nullsLast(), p.ID.asc());
            };
        }

        Cursor cursor(final Punishment last) {
            final Instant key = switch (this) {
                case NEWEST_FIRST, OLDEST_FIRST -> last.createdAt();
                case EXPIRING_FIRST -> last.expiresAt().orElse(null);
            };
            return new Cursor(this.cursorKind(), key, last.id());
        }

        Condition after(final Cursor cursor) {
            final SbPunishments p = SakuraTables.PUNISHMENTS;
            final Instant key = cursor.key();
            return switch (this) {
                case NEWEST_FIRST -> DSL.row(p.CREATED_AT, p.ID)
                        .lt(Preconditions.checkNotNull(key, "key"), cursor.id());
                case OLDEST_FIRST -> DSL.row(p.CREATED_AT, p.ID)
                        .gt(Preconditions.checkNotNull(key, "key"), cursor.id());
                case EXPIRING_FIRST -> key == null
                        ? p.EXPIRES_AT.isNull().and(p.ID.gt(cursor.id()))
                        : p.EXPIRES_AT.isNull().or(DSL.row(p.EXPIRES_AT, p.ID)
                                .gt(key, cursor.id()));
            };
        }
    }
}
