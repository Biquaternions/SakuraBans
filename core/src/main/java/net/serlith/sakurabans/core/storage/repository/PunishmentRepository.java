package net.serlith.sakurabans.core.storage.repository;

import com.google.common.base.Preconditions;
import java.net.InetAddress;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Predicate;
import net.serlith.sakurabans.api.model.Actor;
import net.serlith.sakurabans.api.model.EndKind;
import net.serlith.sakurabans.api.model.ErasedAddressVictim;
import net.serlith.sakurabans.api.model.EvidenceRef;
import net.serlith.sakurabans.api.model.Lengths;
import net.serlith.sakurabans.api.model.Operator;
import net.serlith.sakurabans.api.model.Page;
import net.serlith.sakurabans.api.model.Punishment;
import net.serlith.sakurabans.api.model.PunishmentType;
import net.serlith.sakurabans.api.model.Scope;
import net.serlith.sakurabans.api.model.Victim;
import net.serlith.sakurabans.api.model.Visibility;
import net.serlith.sakurabans.api.punish.OverridePolicy;
import net.serlith.sakurabans.api.punish.PunishmentEdit;
import net.serlith.sakurabans.api.punish.PunishmentQuery;
import net.serlith.sakurabans.api.punish.PunishmentQuery.Applicability;
import net.serlith.sakurabans.core.model.AuditAction;
import net.serlith.sakurabans.core.model.EndKinds;
import net.serlith.sakurabans.core.model.PlayerCounts;
import net.serlith.sakurabans.core.model.PunishmentTotals;
import net.serlith.sakurabans.core.model.SakuraPage;
import net.serlith.sakurabans.core.model.SubjectType;
import net.serlith.sakurabans.core.storage.AddressHasher;
import net.serlith.sakurabans.core.storage.StorageService;
import net.serlith.sakurabans.core.storage.dbo.SakuraTables;
import net.serlith.sakurabans.core.storage.dbo.VictimKind;
import net.serlith.sakurabans.core.storage.dbo.records.SbActivePunishmentsRecord;
import net.serlith.sakurabans.core.storage.dbo.records.SbPunishmentsRecord;
import net.serlith.sakurabans.core.storage.dbo.tables.SbActivePunishments;
import net.serlith.sakurabans.core.storage.dbo.tables.SbPunishments;
import net.serlith.sakurabans.core.storage.dbo.tables.SbVictims;
import net.serlith.sakurabans.core.util.Scopes;
import net.serlith.sakurabans.core.util.Uuids;
import org.jooq.Condition;
import org.jooq.DSLContext;
import org.jooq.Record;
import org.jooq.ResultQuery;
import org.jooq.SortField;
import org.jooq.impl.DSL;
import org.jspecify.annotations.Nullable;

public final class PunishmentRepository {

    private final StorageService storage;
    private final AddressHasher hasher;
    private final PunishmentMapper mapper;
    private final AltPolicy policy;

    public PunishmentRepository(final StorageService storage, final AddressHasher hasher,
            final PunishmentMapper mapper) {
        this(storage, hasher, mapper, AltPolicy.NONE);
    }

    public PunishmentRepository(final StorageService storage, final AddressHasher hasher,
            final PunishmentMapper mapper, final AltPolicy policy) {
        this.storage = storage;
        this.hasher = hasher;
        this.mapper = mapper;
        this.policy = policy;
    }

    public CompletableFuture<InsertOutcome> insert(final NewPunishment punishment,
            final OverridePolicy policy) {
        return this.insert(punishment, policy, null);
    }

    /** {@code use}: the template use the punishment comes from, written with it. */
    public CompletableFuture<InsertOutcome> insert(final NewPunishment punishment,
            final OverridePolicy policy, final @Nullable NewTemplateUse use) {
        return this.insert(punishment, policy, use, null);
    }

    /**
     * {@code expected}: the ladder step the use was counted at (S64). The use's victim is then
     * locked first and counted again, and nothing is stored when the step changed meanwhile
     * ({@link InsertOutcome.StepChanged}). The lock comes before the active slot's, as on every
     * path that takes both, so the two can't deadlock.
     */
    public CompletableFuture<InsertOutcome> insert(final NewPunishment punishment,
            final OverridePolicy policy, final @Nullable NewTemplateUse use,
            final @Nullable ExpectedStep expected) {
        return this.insert(punishment, policy, use, expected, existing -> true);
    }

    public CompletableFuture<InsertOutcome> insert(final NewPunishment punishment,
            final OverridePolicy policy, final @Nullable NewTemplateUse use,
            final @Nullable ExpectedStep expected, final Predicate<Punishment> replaceable) {
        Preconditions.checkArgument(expected == null || use != null,
                "an expected step needs its use");
        return this.storage.writeRetryingConflicts(configuration -> {
            final DSLContext dsl = configuration.dsl();
            if (expected != null) {
                final OptionalInt changed = TemplateRepository.changedStep(dsl, use.victim(),
                        expected, this.policy, this.hasher);
                if (changed.isPresent()) {
                    return new InsertOutcome.StepChanged(changed.getAsInt());
                }
            }
            final InsertOutcome outcome = this.insert(dsl, punishment, policy, replaceable);
            if (use != null && outcome instanceof InsertOutcome.Inserted inserted) {
                TemplateRepository.insertUse(dsl, this.hasher, use,
                        inserted.punishment().id());
            }
            return outcome;
        });
    }

    private InsertOutcome insert(final DSLContext dsl, final NewPunishment punishment,
            final OverridePolicy policy, final Predicate<Punishment> replaceable) {
        final SbActivePunishments active = SakuraTables.ACTIVE_PUNISHMENTS;
        final long victimId = VictimRepository.resolve(dsl, this.hasher, punishment.victim());
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
                    final Punishment existing =
                            this.fetch(dsl, occupant.punishmentId()).orElseThrow();
                    if (!replaceable.test(existing)) {
                        return new InsertOutcome.Protected(existing);
                    }
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
        final Punishment inserted = this.fetch(dsl, id).orElseThrow();
        AuditRepository.append(dsl, punishment.createdAt(), punishment.actor(), AuditAction.CREATE,
                SubjectType.PUNISHMENT, Long.toString(id), null, AuditValues.summary(inserted));
        return new InsertOutcome.Inserted(inserted, superseded);
    }

    private static long insertRow(final DSLContext dsl, final NewPunishment punishment,
            final long victimId, final int scopeId) {
        final SbPunishments p = SakuraTables.PUNISHMENTS;
        final StoredOperator operator = StoredOperator.of(punishment.actor().operator());
        return dsl.insertInto(p)
                .set(p.TYPE, punishment.type())
                .set(p.SHADOW, punishment.shadow())
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
                .set(p.TEMPLATE, punishment.template())
                .set(p.TEMPLATE_VARIANT_SCOPE_ID, punishment.templateVariant() == null ? null
                        : ScopeRepository.resolve(dsl, punishment.templateVariant()))
                .set(p.TEMPLATE_STEP, punishment.templateStep() == null ? null
                        : punishment.templateStep().shortValue())
                .set(p.EXTERNAL_ID, punishment.externalId())
                .set(p.ACKNOWLEDGED, false)
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
            if (!endInTransaction(dsl, id, kind, at, actor, reason)) {
                return Optional.<Punishment>empty();
            }
            if (actor != null && (kind == EndKind.REVOKED || kind == EndKind.ROLLED_BACK)) {
                AuditRepository.append(dsl, at, actor, kind == EndKind.REVOKED
                                ? AuditAction.REVOKE : AuditAction.ROLLBACK,
                        SubjectType.PUNISHMENT, Long.toString(id), null, reason);
            }
            return this.fetch(dsl, id);
        });
    }

    static boolean endInTransaction(final DSLContext dsl, final long id,
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
                .where(p.ID.eq(id))
                .execute();
        return true;
    }

    public CompletableFuture<ChangeOutcome> edit(final long id, final PunishmentEdit edit,
            final Actor actor, final Instant now) {
        return this.storage.writeRetryingConflicts(configuration -> {
            final DSLContext dsl = configuration.dsl();
            final SbActivePunishmentsRecord activeRow = lockActive(dsl, id);
            final SbPunishmentsRecord row = lock(dsl, id);
            if (row == null) {
                return new ChangeOutcome.NotFound();
            }
            final SbPunishments p = SakuraTables.PUNISHMENTS;
            final SbActivePunishments active = SakuraTables.ACTIVE_PUNISHMENTS;
            final Punishment previous = this.fetch(dsl, id).orElseThrow();
            final boolean isActive = activeRow != null && (activeRow.expiresAt() == null
                    || activeRow.expiresAt().isAfter(now));
            final String subject = Long.toString(id);
            switch (edit) {
                case PunishmentEdit.NewReason reason -> {
                    dsl.update(p).set(p.REASON, reason.reason()).where(p.ID.eq(id)).execute();
                    AuditRepository.append(dsl, now, actor, AuditAction.EDIT_REASON,
                            SubjectType.PUNISHMENT, subject, row.reason(), reason.reason());
                }
                case PunishmentEdit.NewExpiry expiry -> {
                    if (!isActive) {
                        return new ChangeOutcome.Refused(ChangeOutcome.Problem.NOT_ACTIVE);
                    }
                    final Instant at = expiry.expiresAt().orElse(null);
                    if (at != null && (!at.isAfter(row.createdAt()) || !at.isAfter(now))) {
                        return new ChangeOutcome.Refused(ChangeOutcome.Problem.INVALID_EXPIRY);
                    }
                    dsl.update(p).set(p.EXPIRES_AT, at).where(p.ID.eq(id)).execute();
                    dsl.update(active).set(active.EXPIRES_AT, at)
                            .where(active.PUNISHMENT_ID.eq(id)).execute();
                    AuditRepository.append(dsl, now, actor, AuditAction.EDIT_EXPIRY,
                            SubjectType.PUNISHMENT, subject, AuditValues.expiry(row.expiresAt()),
                            AuditValues.expiry(at));
                }
                case PunishmentEdit.NewScope scope -> {
                    if (!isActive) {
                        return new ChangeOutcome.Refused(ChangeOutcome.Problem.NOT_ACTIVE);
                    }
                    final int scopeId = ScopeRepository.resolve(dsl, scope.scope());
                    if (!row.type().stacks()) {
                        final SbActivePunishmentsRecord occupant = dsl.selectFrom(active)
                                .where(active.TYPE.eq(row.type()))
                                .and(active.VICTIM_ID.eq(row.victimId()))
                                .and(active.SCOPE_ID.eq(scopeId))
                                .and(active.PUNISHMENT_ID.ne(id))
                                .forUpdate()
                                .fetchOne();
                        if (occupant != null) {
                            final Instant expiry = occupant.expiresAt();
                            if (expiry == null || expiry.isAfter(now)) {
                                return new ChangeOutcome.Refused(
                                        ChangeOutcome.Problem.ALREADY_PUNISHED);
                            }
                            endInTransaction(dsl, occupant.punishmentId(), EndKind.EXPIRED,
                                    expiry, null, null);
                        }
                    }
                    dsl.update(p).set(p.SCOPE_ID, scopeId).where(p.ID.eq(id)).execute();
                    dsl.update(active).set(active.SCOPE_ID, scopeId)
                            .where(active.PUNISHMENT_ID.eq(id)).execute();
                    AuditRepository.append(dsl, now, actor, AuditAction.EDIT_SCOPE,
                            SubjectType.PUNISHMENT, subject, Scopes.format(previous.scope()),
                            Scopes.format(scope.scope()));
                }
            }
            return new ChangeOutcome.Changed(previous, this.fetch(dsl, id).orElseThrow());
        });
    }

    public CompletableFuture<ChangeOutcome> voidPunishment(final long id, final Actor actor,
            final @Nullable String reason, final Instant now) {
        return this.storage.write(configuration -> {
            final DSLContext dsl = configuration.dsl();
            lockActive(dsl, id);
            final SbPunishmentsRecord row = lock(dsl, id);
            if (row == null) {
                return new ChangeOutcome.NotFound();
            }
            if (row.voidedAt() != null) {
                return new ChangeOutcome.Refused(ChangeOutcome.Problem.ALREADY_VOIDED);
            }
            final Punishment previous = this.fetch(dsl, id).orElseThrow();
            final SbPunishments p = SakuraTables.PUNISHMENTS;
            final StoredOperator by = StoredOperator.of(actor.operator());
            dsl.update(p)
                    .set(p.VOIDED_AT, now)
                    .set(p.VOIDER_KIND, by.kind())
                    .set(p.VOIDER_UUID, by.uuid())
                    .set(p.VOIDER_NAME, by.name())
                    .set(p.VOIDER_SOURCE, actor.source())
                    .set(p.VOID_REASON, reason)
                    .where(p.ID.eq(id))
                    .execute();
            endInTransaction(dsl, id, EndKind.VOIDED, now, actor, reason);
            AuditRepository.append(dsl, now, actor, AuditAction.VOID, SubjectType.PUNISHMENT,
                    Long.toString(id), null, reason);
            return new ChangeOutcome.Changed(previous, this.fetch(dsl, id).orElseThrow());
        });
    }

    public CompletableFuture<ChangeOutcome> expunge(final long id, final Actor actor,
            final Instant now) {
        return this.storage.write(configuration -> {
            final DSLContext dsl = configuration.dsl();
            lockActive(dsl, id);
            final SbPunishmentsRecord row = lock(dsl, id);
            if (row == null) {
                return new ChangeOutcome.NotFound();
            }
            final Punishment previous = this.fetch(dsl, id).orElseThrow();
            dsl.deleteFrom(SakuraTables.PUNISHMENT_EVIDENCE)
                    .where(SakuraTables.PUNISHMENT_EVIDENCE.PUNISHMENT_ID.eq(id)).execute();
            dsl.deleteFrom(SakuraTables.ACTIVE_PUNISHMENTS)
                    .where(SakuraTables.ACTIVE_PUNISHMENTS.PUNISHMENT_ID.eq(id)).execute();
            // Its template use goes too (also by the foreign key's cascade).
            dsl.deleteFrom(SakuraTables.TEMPLATE_USES)
                    .where(SakuraTables.TEMPLATE_USES.PUNISHMENT_ID.eq(id)).execute();
            dsl.deleteFrom(SakuraTables.PUNISHMENTS)
                    .where(SakuraTables.PUNISHMENTS.ID.eq(id)).execute();
            AuditRepository.append(dsl, now, actor, AuditAction.EXPUNGE, SubjectType.PUNISHMENT,
                    Long.toString(id), AuditValues.summary(previous), null);
            return new ChangeOutcome.Changed(previous, previous);
        });
    }

    public CompletableFuture<ChangeOutcome> evidence(final long id, final EvidenceRef ref,
            final boolean attach, final Actor actor, final Instant now) {
        return this.storage.write(configuration -> {
            final DSLContext dsl = configuration.dsl();
            final SbPunishmentsRecord row = lock(dsl, id);
            if (row == null) {
                return new ChangeOutcome.NotFound();
            }
            final var evidence = SakuraTables.PUNISHMENT_EVIDENCE;
            final Condition same = evidence.PUNISHMENT_ID.eq(id).and(evidence.URI.eq(ref.uri()));
            final Punishment previous = this.fetch(dsl, id).orElseThrow();
            if (attach) {
                if (dsl.fetchExists(evidence, same)) {
                    return new ChangeOutcome.Refused(
                            ChangeOutcome.Problem.EVIDENCE_ALREADY_ATTACHED);
                }
                final StoredOperator by = StoredOperator.of(actor.operator());
                dsl.insertInto(evidence)
                        .set(evidence.PUNISHMENT_ID, id)
                        .set(evidence.URI, ref.uri())
                        .set(evidence.ADDED_BY_KIND, by.kind())
                        .set(evidence.ADDED_BY_UUID, by.uuid())
                        .set(evidence.ADDED_BY_NAME, by.name())
                        .set(evidence.ADDED_BY_SOURCE, actor.source())
                        .set(evidence.ADDED_AT, now)
                        .execute();
            } else if (dsl.deleteFrom(evidence).where(same).execute() == 0) {
                return new ChangeOutcome.Refused(ChangeOutcome.Problem.EVIDENCE_NOT_ATTACHED);
            }
            AuditRepository.append(dsl, now, actor, attach ? AuditAction.EVIDENCE_ATTACH
                            : AuditAction.EVIDENCE_DETACH, SubjectType.PUNISHMENT,
                    Long.toString(id), attach ? null : ref.uri(), attach ? ref.uri() : null);
            return new ChangeOutcome.Changed(previous, this.fetch(dsl, id).orElseThrow());
        });
    }

    private static @Nullable SbActivePunishmentsRecord lockActive(final DSLContext dsl,
            final long id) {
        final SbActivePunishments active = SakuraTables.ACTIVE_PUNISHMENTS;
        return dsl.selectFrom(active).where(active.PUNISHMENT_ID.eq(id)).forUpdate().fetchOne();
    }

    private static @Nullable SbPunishmentsRecord lock(final DSLContext dsl, final long id) {
        return dsl.selectFrom(SakuraTables.PUNISHMENTS)
                .where(SakuraTables.PUNISHMENTS.ID.eq(id))
                .forUpdate()
                .fetchOne();
    }

    public CompletableFuture<List<Punishment>> active(final PunishmentType type,
            final Victim victim, final @Nullable Scope scope, final Instant now) {
        final SbPunishments p = SakuraTables.PUNISHMENTS;
        final SbActivePunishments active = SakuraTables.ACTIVE_PUNISHMENTS;
        return this.storage.read(dsl -> this.mapper.map(dsl, PunishmentMapper.select(dsl)
                .join(active).on(active.PUNISHMENT_ID.eq(p.ID))
                .where(active.TYPE.eq(type))
                .and(StoredVictim.of(victim, this.hasher).matches(SakuraTables.VICTIMS))
                .and(scope == null ? DSL.noCondition() : ScopeRepository.matches(scope))
                .and(unexpired(now))
                .orderBy(p.CREATED_AT.desc(), p.ID.desc())
                .fetch()));
    }

    public CompletableFuture<List<Punishment>> activeIssuedBy(final Operator operator,
            final Instant since, final Instant now) {
        final SbPunishments p = SakuraTables.PUNISHMENTS;
        final SbActivePunishments active = SakuraTables.ACTIVE_PUNISHMENTS;
        return this.storage.read(dsl -> this.mapper.map(dsl, PunishmentMapper.select(dsl)
                .join(active).on(active.PUNISHMENT_ID.eq(p.ID))
                .where(StoredOperator.identity(operator, p.OPERATOR_KIND, p.OPERATOR_UUID,
                        p.OPERATOR_NAME))
                .and(p.CREATED_AT.ge(since))
                .and(unexpired(now))
                .orderBy(p.ID.asc())
                .fetch()));
    }

    public CompletableFuture<List<Punishment>> endedOf(final Victim victim,
            final @Nullable Instant before) {
        final SbPunishments p = SakuraTables.PUNISHMENTS;
        return this.storage.read(dsl -> this.mapper.map(dsl, PunishmentMapper.select(dsl)
                .where(StoredVictim.of(victim, this.hasher).matches(SakuraTables.VICTIMS))
                .and(p.ID.notIn(DSL.select(SakuraTables.ACTIVE_PUNISHMENTS.PUNISHMENT_ID)
                        .from(SakuraTables.ACTIVE_PUNISHMENTS)))
                .and(before == null ? DSL.noCondition() : p.CREATED_AT.lt(before))
                .orderBy(p.ID.asc())
                .fetch()));
    }

    public CompletableFuture<List<Punishment>> activeRanges(final Instant now) {
        final SbPunishments p = SakuraTables.PUNISHMENTS;
        final SbActivePunishments active = SakuraTables.ACTIVE_PUNISHMENTS;
        return this.storage.read(dsl -> this.mapper.map(dsl, PunishmentMapper.select(dsl)
                .join(active).on(active.PUNISHMENT_ID.eq(p.ID))
                .where(SakuraTables.VICTIMS.KIND.eq(VictimKind.ADDRESS_RANGE))
                .and(unexpired(now))
                .fetch()));
    }

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

    public CompletableFuture<Long> activeWarnings(final UUID uuid, final long upToId,
            final Instant now) {
        final SbActivePunishments active = SakuraTables.ACTIVE_PUNISHMENTS;
        final SbVictims victims = SakuraTables.VICTIMS;
        return this.storage.read(dsl -> (long) dsl.fetchCount(DSL.selectOne().from(active)
                .join(victims).on(victims.ID.eq(active.VICTIM_ID))
                .where(active.TYPE.eq(PunishmentType.WARN))
                .and(victims.UUID.eq(uuid))
                .and(victims.KIND.in(VictimKind.PLAYER, VictimKind.COMPOSITE))
                .and(active.PUNISHMENT_ID.le(upToId))
                .and(unexpired(now))));
    }

    public CompletableFuture<PunishmentTotals> totals(final Instant now) {
        final SbPunishments p = SakuraTables.PUNISHMENTS;
        final SbActivePunishments active = SakuraTables.ACTIVE_PUNISHMENTS;
        return this.storage.read(dsl -> {
            final Map<PunishmentType, Long> total = new EnumMap<>(PunishmentType.class);
            dsl.select(p.TYPE, DSL.count()).from(p)
                    .where(p.VOIDED_AT.isNull())
                    .groupBy(p.TYPE)
                    .fetch()
                    .forEach(row -> total.put(row.value1(), (long) row.value2()));
            final Map<PunishmentType, Long> current = new EnumMap<>(PunishmentType.class);
            dsl.select(active.TYPE, DSL.count()).from(active)
                    .where(unexpired(now))
                    .groupBy(active.TYPE)
                    .fetch()
                    .forEach(row -> current.put(row.value1(), (long) row.value2()));
            return new PunishmentTotals(current.getOrDefault(PunishmentType.BAN, 0L),
                    current.getOrDefault(PunishmentType.MUTE, 0L),
                    current.getOrDefault(PunishmentType.WARN, 0L),
                    total.getOrDefault(PunishmentType.BAN, 0L),
                    total.getOrDefault(PunishmentType.MUTE, 0L),
                    total.getOrDefault(PunishmentType.WARN, 0L),
                    total.getOrDefault(PunishmentType.KICK, 0L));
        });
    }

    public CompletableFuture<PlayerCounts> countsOf(final UUID uuid, final Instant now) {
        final SbPunishments p = SakuraTables.PUNISHMENTS;
        final SbActivePunishments active = SakuraTables.ACTIVE_PUNISHMENTS;
        final SbVictims victims = SakuraTables.VICTIMS;
        return this.storage.read(dsl -> {
            final Map<PunishmentType, Long> received = new EnumMap<>(PunishmentType.class);
            long shadowMutes = 0;
            for (final var row : dsl.select(p.TYPE, p.SHADOW, DSL.count()).from(p)
                    .join(victims).on(victims.ID.eq(p.VICTIM_ID))
                    .where(victims.UUID.eq(uuid))
                    .and(victims.KIND.in(VictimKind.PLAYER, VictimKind.COMPOSITE))
                    .and(p.VOIDED_AT.isNull())
                    .groupBy(p.TYPE, p.SHADOW)
                    .fetch()) {
                received.merge(row.value1(), (long) row.value3(), Long::sum);
                if (row.value1() == PunishmentType.MUTE && Boolean.TRUE.equals(row.value2())) {
                    shadowMutes += row.value3();
                }
            }
            long permanent = 0;
            final List<Instant> expiries = new ArrayList<>();
            for (final Instant expiry : dsl.select(active.EXPIRES_AT).from(active)
                    .join(victims).on(victims.ID.eq(active.VICTIM_ID))
                    .where(active.TYPE.eq(PunishmentType.WARN))
                    .and(victims.UUID.eq(uuid))
                    .and(victims.KIND.in(VictimKind.PLAYER, VictimKind.COMPOSITE))
                    .and(unexpired(now))
                    .fetch(active.EXPIRES_AT)) {
                if (expiry == null) {
                    permanent++;
                } else {
                    expiries.add(expiry);
                }
            }
            return new PlayerCounts(received.getOrDefault(PunishmentType.BAN, 0L),
                    received.getOrDefault(PunishmentType.MUTE, 0L), shadowMutes,
                    received.getOrDefault(PunishmentType.WARN, 0L),
                    received.getOrDefault(PunishmentType.KICK, 0L), permanent, expiries);
        });
    }

    public CompletableFuture<List<Punishment>> unacknowledgedWarnings(final UUID uuid,
            final Instant now) {
        final SbPunishments p = SakuraTables.PUNISHMENTS;
        final SbActivePunishments active = SakuraTables.ACTIVE_PUNISHMENTS;
        final SbVictims victims = SakuraTables.VICTIMS;
        return this.storage.read(dsl -> this.mapper.map(dsl, PunishmentMapper.select(dsl)
                .join(active).on(active.PUNISHMENT_ID.eq(p.ID))
                .where(active.TYPE.eq(PunishmentType.WARN))
                .and(victims.UUID.eq(uuid))
                .and(victims.KIND.in(VictimKind.PLAYER, VictimKind.COMPOSITE))
                .and(p.ACKNOWLEDGED.eq(false))
                .and(unexpired(now))
                .orderBy(p.CREATED_AT.asc(), p.ID.asc())
                .fetch()));
    }

    public CompletableFuture<Boolean> acknowledge(final long id) {
        final SbPunishments p = SakuraTables.PUNISHMENTS;
        return this.storage.write(configuration -> configuration.dsl().update(p)
                .set(p.ACKNOWLEDGED, true)
                .where(p.ID.eq(id))
                .and(p.ACKNOWLEDGED.eq(false))
                .execute() == 1);
    }

    public CompletableFuture<List<Punishment>> activeApplicable(final PunishmentType type,
            final Applicability applicability, final Collection<? extends Scope> scopes,
            final Instant now) {
        return this.storage.read(dsl -> this.mapper.map(dsl, activeApplicableQuery(dsl, type,
                applicability, scopes, now, this.policy, this.hasher).fetch()));
    }

    public CompletableFuture<List<Punishment>> activeApplicableInEveryScope(
            final PunishmentType type, final Applicability applicability, final Instant now) {
        return this.storage.read(dsl -> this.mapper.map(dsl, activeApplicableQuery(dsl, type,
                applicability, DSL.noCondition(), now, this.policy, this.hasher).fetch()));
    }

    public CompletableFuture<Optional<List<Punishment>>> restoreErased(
            final List<Punishment> matched, final UUID uuid, final InetAddress address) {
        final String hash = this.hasher.hex(address);
        final List<ErasedAddressVictim> erased = matched.stream()
                .map(Punishment::victim)
                .filter(ErasedAddressVictim.class::isInstance)
                .map(ErasedAddressVictim.class::cast)
                .filter(victim -> victim.hash().equals(hash)
                        && victim.uuid().map(uuid::equals).orElse(true))
                .distinct()
                .toList();
        if (erased.isEmpty()) {
            return CompletableFuture.completedFuture(Optional.empty());
        }
        return this.storage.write(configuration -> {
            final DSLContext dsl = configuration.dsl();
            for (final ErasedAddressVictim victim : erased) {
                VictimRepository.restore(dsl, this.hasher, victim.uuid().isPresent()
                                ? VictimKind.COMPOSITE : VictimKind.ADDRESS,
                        victim.uuid().orElse(Uuids.ZERO), address);
            }
            final List<Punishment> reloaded = new ArrayList<>(matched.size());
            for (final Punishment punishment : matched) {
                reloaded.add(erased.contains(punishment.victim())
                        ? this.fetch(dsl, punishment.id()).orElse(punishment)
                        : punishment);
            }
            return Optional.of(List.copyOf(reloaded));
        });
    }

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

    public CompletableFuture<Boolean> reaches(final long punishmentId,
            final Applicability applicability, final Instant now) {
        final SbPunishments p = SakuraTables.PUNISHMENTS;
        return this.storage.read(dsl -> dsl.fetchExists(DSL.selectOne().from(p)
                .where(p.ID.eq(punishmentId))
                .and(ApplicabilityConditions.applies(p.VICTIM_ID, p.CREATED_AT, p.TYPE,
                        applicability, this.policy, this.hasher, now))));
    }

    static ResultQuery<Record> activeApplicableQuery(final DSLContext dsl,
            final PunishmentType type, final Applicability applicability,
            final Collection<? extends Scope> scopes, final Instant now,
            final AltPolicy policy, final AddressHasher hasher) {
        final Condition inScopes = DSL.or(scopes.stream().map(ScopeRepository::matches).toList());
        return activeApplicableQuery(dsl, type, applicability,
                SakuraTables.ACTIVE_PUNISHMENTS.SCOPE_ID.in(DSL.select(SakuraTables.SCOPES.ID)
                        .from(SakuraTables.SCOPES).where(inScopes)),
                now, policy, hasher);
    }

    private static ResultQuery<Record> activeApplicableQuery(final DSLContext dsl,
            final PunishmentType type, final Applicability applicability,
            final Condition scoped, final Instant now, final AltPolicy policy,
            final AddressHasher hasher) {
        final SbPunishments p = SakuraTables.PUNISHMENTS;
        final SbActivePunishments active = SakuraTables.ACTIVE_PUNISHMENTS;
        return PunishmentMapper.select(dsl)
                .join(active).on(active.PUNISHMENT_ID.eq(p.ID))
                .where(active.TYPE.eq(type))
                .and(scoped)
                .and(ApplicabilityConditions.applies(active.VICTIM_ID, p.CREATED_AT, active.TYPE,
                        applicability, policy, hasher, now))
                .and(unexpired(now))
                .orderBy(active.EXPIRES_AT.desc().nullsFirst(), p.ID.desc());
    }

    public CompletableFuture<Page<Punishment>> query(final PunishmentQuery query,
            final Instant now) {
        return this.storage.read(dsl -> {
            final List<Condition> conditions = conditions(query, now, this.policy, this.hasher);
            final long counted = count(dsl, conditions);
            final long offset = (long) (query.page() - 1) * query.pageSize();
            final List<Record> rows = PunishmentMapper.select(dsl)
                    .where(conditions)
                    .orderBy(sort(query.order()))
                    .limit(query.pageSize())
                    .offset(offset)
                    .fetch();
            // Two statements under READ COMMITTED: a row written between them can leave the
            // count short of what the page holds.
            final long total = rows.isEmpty()
                    ? counted : Math.max(counted, offset + rows.size());
            return (Page<Punishment>) new SakuraPage<>(this.mapper.map(dsl, rows), total,
                    query.page(), query.pageSize());
        });
    }

    public CompletableFuture<Long> count(final PunishmentQuery query, final Instant now) {
        return this.storage.read(dsl -> count(dsl,
                conditions(query, now, this.policy, this.hasher)));
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

    static List<Condition> conditions(final PunishmentQuery query, final Instant now,
            final AltPolicy policy, final AddressHasher hasher) {
        final SbPunishments p = SakuraTables.PUNISHMENTS;
        final SbActivePunishments active = SakuraTables.ACTIVE_PUNISHMENTS;
        final List<Condition> conditions = new ArrayList<>();
        if (!query.types().isEmpty()) {
            conditions.add(p.TYPE.in(query.types()));
        }
        query.victim().ifPresent(victim ->
                conditions.add(StoredVictim.of(victim, hasher).matches(SakuraTables.VICTIMS)));
        query.applicableTo().ifPresent(applicability -> conditions.add(
                ApplicabilityConditions.applies(p.VICTIM_ID, p.CREATED_AT, p.TYPE,
                        applicability, policy, hasher, now)));
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

    private Optional<Punishment> fetch(final DSLContext dsl, final long id) {
        final SbPunishments p = SakuraTables.PUNISHMENTS;
        final List<Punishment> found =
                this.mapper.map(dsl, PunishmentMapper.select(dsl).where(p.ID.eq(id)).fetch());
        return found.stream().findFirst();
    }

    private static List<SortField<?>> sort(final PunishmentQuery.Order order) {
        final SbPunishments p = SakuraTables.PUNISHMENTS;
        return switch (order) {
            case NEWEST_FIRST -> List.of(p.CREATED_AT.desc(), p.ID.desc());
            case OLDEST_FIRST -> List.of(p.CREATED_AT.asc(), p.ID.asc());
            case EXPIRING_FIRST -> List.of(p.EXPIRES_AT.asc().nullsLast(), p.ID.asc());
        };
    }
}
