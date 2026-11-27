package net.serlith.sakurabans.core.storage.repository;

import java.net.InetAddress;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;
import net.serlith.sakurabans.api.model.Actor;
import net.serlith.sakurabans.api.model.AddressRangeVictim;
import net.serlith.sakurabans.api.model.AddressStrictness;
import net.serlith.sakurabans.api.model.AddressVictim;
import net.serlith.sakurabans.api.model.CompositeVictim;
import net.serlith.sakurabans.api.model.ErasedAddressVictim;
import net.serlith.sakurabans.api.model.GlobalScope;
import net.serlith.sakurabans.api.model.PlayerVictim;
import net.serlith.sakurabans.api.model.Scope;
import net.serlith.sakurabans.api.model.Victim;
import net.serlith.sakurabans.api.punish.PunishmentQuery.Applicability;
import net.serlith.sakurabans.api.template.LadderTemplate;
import net.serlith.sakurabans.api.template.PresetTemplate;
import net.serlith.sakurabans.api.template.TemplateDuration;
import net.serlith.sakurabans.api.template.TemplateInfo;
import net.serlith.sakurabans.api.template.TemplateStep;
import net.serlith.sakurabans.core.model.AuditAction;
import net.serlith.sakurabans.core.model.StoredTemplate;
import net.serlith.sakurabans.core.model.SubjectType;
import net.serlith.sakurabans.core.model.TemplateBuilder;
import net.serlith.sakurabans.core.model.TemplateKind;
import net.serlith.sakurabans.core.model.TemplateSummaries;
import net.serlith.sakurabans.core.storage.StorageService;
import net.serlith.sakurabans.core.storage.dbo.DurationMode;
import net.serlith.sakurabans.core.storage.dbo.SakuraTables;
import net.serlith.sakurabans.core.storage.dbo.ScopeKind;
import net.serlith.sakurabans.core.storage.dbo.VictimKind;
import net.serlith.sakurabans.core.storage.dbo.records.SbTemplatesRecord;
import net.serlith.sakurabans.core.storage.dbo.tables.SbPunishments;
import net.serlith.sakurabans.core.storage.dbo.tables.SbTemplateGroupMembers;
import net.serlith.sakurabans.core.storage.dbo.tables.SbTemplateGroups;
import net.serlith.sakurabans.core.storage.dbo.tables.SbTemplateSteps;
import net.serlith.sakurabans.core.storage.dbo.tables.SbTemplateUses;
import net.serlith.sakurabans.core.storage.dbo.tables.SbTemplates;
import net.serlith.sakurabans.core.storage.dbo.tables.SbVictims;
import net.serlith.sakurabans.core.util.AddressHashes;
import net.serlith.sakurabans.core.util.Scopes;
import org.jooq.Condition;
import org.jooq.DSLContext;
import org.jooq.Field;
import org.jooq.Record1;
import org.jooq.Record2;
import org.jooq.Select;
import org.jooq.impl.DSL;
import org.jspecify.annotations.Nullable;

public final class TemplateRepository {

    private static final String SEPARATOR = "\n";

    private final StorageService storage;
    private final AltPolicy policy;

    public TemplateRepository(final StorageService storage, final AltPolicy policy) {
        this.storage = storage;
        this.policy = policy;
    }

    public sealed interface Write {

        record Done(StoredTemplate value) implements Write {
        }

        /** A global template deleted with its variants, or a variant alone. */
        record Deleted(StoredTemplate value, List<Scope> variants) implements Write {

            public Deleted {
                variants = List.copyOf(variants);
            }
        }

        record Unchanged(StoredTemplate value) implements Write {
        }

        record Taken(StoredTemplate existing) implements Write {
        }

        record NotFound() implements Write {
        }

        record Conflict(long currentVersion) implements Write {
        }

        /** A variant whose name has no global template. */
        record NoGlobal() implements Write {
        }

        /**
         * A variant of another kind than its global template ({@code variants} empty), or a
         * global template changing kind while these variants keep the old one.
         */
        record KindMismatch(TemplateKind expected, List<Scope> variants) implements Write {

            public KindMismatch {
                variants = List.copyOf(variants);
            }
        }
    }

    /** Every definition: the global ones and every variant. */
    public CompletableFuture<List<StoredTemplate>> all() {
        return this.storage.read(TemplateRepository::load);
    }

    /** {@code definition}: made by {@link TemplateBuilder}, so valid. */
    public CompletableFuture<Write> create(
            final TemplateInfo definition, final Actor actor, final Instant at) {
        return this.storage.writeRetryingConflicts(configuration -> {
            final DSLContext dsl = configuration.dsl();
            final Scope variant = variantOf(definition);
            final int variantId = ScopeRepository.resolve(dsl, variant);
            final Optional<StoredTemplate> existing =
                    row(dsl, variantId, definition.name()).map(row -> assemble(dsl, row));
            if (existing.isPresent()) {
                return new Write.Taken(existing.get());
            }
            final TemplateKind kind = TemplateKind.of(definition);
            if (definition.variant().isPresent()) {
                final SbTemplatesRecord global = globalRow(dsl, definition.name(), true)
                        .orElse(null);
                if (global == null) {
                    return new Write.NoGlobal();
                }
                if (global.kind() != kind) {
                    return new Write.KindMismatch(global.kind(), List.of());
                }
            }
            final SbTemplates t = SakuraTables.TEMPLATES;
            final int id = dsl.insertInto(t)
                    .set(t.NAME, definition.name())
                    .set(t.VARIANT_SCOPE_ID, variantId)
                    .set(t.CREATED_AT, at)
                    .set(t.VERSION, 1L)
                    .set(values(dsl, definition, at))
                    .returningResult(t.ID)
                    .fetchSingle()
                    .value1();
            writeChildren(dsl, id, definition);
            AuditRepository.append(dsl, at, actor, AuditAction.CREATE, SubjectType.TEMPLATE,
                    subject(variant, definition.name()), null,
                    TemplateSummaries.summary(definition));
            return new Write.Done(new StoredTemplate(definition, 1));
        });
    }

    /** {@code definition}: made by {@link TemplateBuilder}, so valid. */
    public CompletableFuture<Write> update(
            final TemplateInfo definition, final @Nullable Long expectedVersion,
            final Actor actor, final Instant at) {
        return this.storage.writeRetryingConflicts(configuration -> {
            final DSLContext dsl = configuration.dsl();
            final Scope variant = variantOf(definition);
            final Integer variantId = ScopeRepository.lookup(dsl, variant).orElse(null);
            final SbTemplatesRecord row = variantId == null ? null
                    : row(dsl, variantId, definition.name()).orElse(null);
            if (row == null) {
                return new Write.NotFound();
            }
            if (expectedVersion != null && row.version() != expectedVersion.longValue()) {
                return new Write.Conflict(row.version());
            }
            final TemplateKind kind = TemplateKind.of(definition);
            if (definition.variant().isPresent()) {
                final SbTemplatesRecord global = globalRow(dsl, definition.name(), false)
                        .orElse(null);
                if (global == null) {
                    return new Write.NoGlobal();
                }
                if (global.kind() != kind) {
                    return new Write.KindMismatch(global.kind(), List.of());
                }
            } else if (row.kind() != kind) {
                final List<Scope> variants = variants(dsl, definition.name());
                if (!variants.isEmpty()) {
                    return new Write.KindMismatch(row.kind(), variants);
                }
            }
            final StoredTemplate previous = assemble(dsl, row);
            final String before = TemplateSummaries.summary(previous.template());
            final String after = TemplateSummaries.summary(definition);
            if (before.equals(after)) {
                return new Write.Unchanged(previous);
            }
            final StoredTemplate next = new StoredTemplate(definition, row.version() + 1);
            final SbTemplates t = SakuraTables.TEMPLATES;
            final int updated = dsl.update(t)
                    .set(values(dsl, definition, at))
                    .set(t.VERSION, next.version())
                    .where(t.ID.eq(row.id()))
                    .and(t.VERSION.eq(row.version()))
                    .execute();
            if (updated == 0) {
                return new Write.Conflict(row(dsl, variantId, definition.name())
                        .map(SbTemplatesRecord::version).orElse(row.version()));
            }
            deleteChildren(dsl, row.id());
            writeChildren(dsl, row.id(), definition);
            AuditRepository.append(dsl, at, actor, AuditAction.UPDATE, SubjectType.TEMPLATE,
                    subject(variant, next.name()), before, after);
            return new Write.Done(next);
        });
    }

    /**
     * Deletes one definition. The global one takes its variants with it, whatever their
     * versions: a variant can't exist without its global template.
     */
    public CompletableFuture<Write> delete(final @Nullable Scope variant, final String name,
            final long expectedVersion, final Actor actor, final Instant at) {
        return this.storage.write(configuration -> {
            final DSLContext dsl = configuration.dsl();
            final Scope where = variant == null ? GlobalScope.INSTANCE : variant;
            final Integer variantId = ScopeRepository.lookup(dsl, where).orElse(null);
            final SbTemplatesRecord row = variantId == null ? null
                    : row(dsl, variantId, name).orElse(null);
            if (row == null) {
                return new Write.NotFound();
            }
            if (row.version() != expectedVersion) {
                return new Write.Conflict(row.version());
            }
            final StoredTemplate previous = assemble(dsl, row);
            final List<Scope> variants = new ArrayList<>();
            if (where instanceof GlobalScope) {
                final SbTemplates t = SakuraTables.TEMPLATES;
                for (final SbTemplatesRecord other : dsl.selectFrom(t)
                        .where(t.NAME.eq(name)).and(t.ID.ne(row.id()))
                        .fetch()) {
                    final StoredTemplate gone = assemble(dsl, other);
                    final Scope scope = gone.variant().orElseThrow();
                    deleteRow(dsl, other.id());
                    AuditRepository.append(dsl, at, actor, AuditAction.DELETE,
                            SubjectType.TEMPLATE, subject(scope, name),
                            TemplateSummaries.summary(gone.template()), null);
                    variants.add(scope);
                }
            }
            deleteRow(dsl, row.id());
            AuditRepository.append(dsl, at, actor, AuditAction.DELETE, SubjectType.TEMPLATE,
                    subject(where, name), TemplateSummaries.summary(previous.template()), null);
            variants.sort(Comparator.comparing(Scopes::format));
            return new Write.Deleted(previous, variants);
        });
    }

    private static void deleteRow(final DSLContext dsl, final int id) {
        deleteChildren(dsl, id);
        dsl.deleteFrom(SakuraTables.TEMPLATES)
                .where(SakuraTables.TEMPLATES.ID.eq(id))
                .execute();
    }

    /** The audit subject: the name for the global definition, else {@code <scope> <name>}. */
    static String subject(final Scope variant, final String name) {
        return variant instanceof GlobalScope ? name : Scopes.format(variant) + " " + name;
    }

    private static Scope variantOf(final TemplateInfo definition) {
        return definition.variant().orElse(GlobalScope.INSTANCE);
    }

    /**
     * How often each template was used on the victim: every use, with or without a punishment,
     * except those whose punishment was voided.
     */
    public CompletableFuture<Map<String, Long>> history(final Set<String> templates,
            final Victim victim, final boolean addressShared, final @Nullable Instant since,
            final Instant now) {
        if (templates.isEmpty()) {
            return CompletableFuture.completedFuture(Map.of());
        }
        return this.storage.read(dsl -> counts(dsl, templates, victim, addressShared, since, now,
                this.policy));
    }

    /** The victim's step on a ladder now ({@link LadderCount}). */
    public CompletableFuture<Integer> step(final Victim victim, final LadderCount count) {
        return this.storage.read(dsl -> countStep(dsl, victim, count, this.policy));
    }

    private static Map<String, Long> counts(final DSLContext dsl, final Set<String> templates,
            final Victim victim, final boolean addressShared, final @Nullable Instant since,
            final Instant now, final AltPolicy policy) {
        if (templates.isEmpty()) {
            return Map.of();
        }
        final SbTemplateUses u = SakuraTables.TEMPLATE_USES;
        final SbPunishments p = SakuraTables.PUNISHMENTS;
        final List<Condition> conditions = new ArrayList<>();
        conditions.add(u.TEMPLATE.in(templates));
        conditions.add(u.VICTIM_ID.in(victims(victim, addressShared, now, policy)));
        conditions.add(DSL.notExists(DSL.selectOne().from(p)
                .where(p.ID.eq(u.PUNISHMENT_ID))
                .and(p.VOIDED_AT.isNotNull())));
        if (since != null) {
            conditions.add(u.CREATED_AT.ge(since));
        }
        final Map<String, Long> counts = new HashMap<>();
        dsl.select(u.TEMPLATE, DSL.count()).from(u)
                .where(conditions)
                .groupBy(u.TEMPLATE)
                .fetch()
                .forEach(row -> counts.put(row.value1(), row.value2().longValue()));
        return Map.copyOf(counts);
    }

    private static int countStep(final DSLContext dsl, final Victim victim,
            final LadderCount count, final AltPolicy policy) {
        long points = 0;
        for (final Map.Entry<String, Long> used : counts(dsl, count.weights().keySet(), victim,
                count.addressShared(), count.since(), count.now(), policy).entrySet()) {
            points += used.getValue() * count.weights().getOrDefault(used.getKey(), 0);
        }
        return count.stepFor().applyAsInt(points);
    }

    /**
     * Locks the victim's row and counts its step again (S64): empty when it is still the
     * expected one, else the step it is now. Called first in the transaction that stores a
     * ladder use, so two applies on one victim can't both store the same step. Uses counted
     * through other victims (an {@code address-shared} ladder counts the alts' and addresses'
     * uses too) aren't under this lock: an apply on an alt at the same moment can still count
     * the same step.
     */
    static OptionalInt changedStep(final DSLContext dsl, final Victim victim,
            final ExpectedStep expected, final AltPolicy policy) {
        VictimRepository.lock(dsl, victim);
        final int now = countStep(dsl, victim, expected.count(), policy);
        return now == expected.step() ? OptionalInt.empty() : OptionalInt.of(now);
    }

    public sealed interface UseOutcome {

        record Recorded(long id) implements UseOutcome {
        }

        /** Nothing stored: the expected step is no longer the victim's; this one is. */
        record StepChanged(int step) implements UseOutcome {
        }
    }

    /**
     * Records a use without a punishment (a ladder step that only runs actions), audited in the
     * same transaction (S63): the victim is the subject ({@link #victimSubject}), the template,
     * step (from 1) and variant are the new value. {@code expected}: the step it was counted at,
     * so nothing is stored when a concurrent use changed it (S64); null: stored whatever the
     * count.
     */
    public CompletableFuture<UseOutcome> recordUse(final NewTemplateUse use,
            final @Nullable ExpectedStep expected) {
        return this.storage.writeRetryingConflicts(configuration -> {
            final DSLContext dsl = configuration.dsl();
            if (expected != null) {
                final OptionalInt changed = changedStep(dsl, use.victim(), expected,
                        this.policy);
                if (changed.isPresent()) {
                    return new UseOutcome.StepChanged(changed.getAsInt());
                }
            }
            final long id = insertUse(dsl, use, null);
            final Subject subject = victimSubject(dsl, use.victim());
            AuditRepository.append(dsl, use.createdAt(), use.actor(), AuditAction.APPLY,
                    subject.type(), subject.id(), null,
                    "template " + use.template()
                            + (use.step() == null ? "" : " step " + (use.step() + 1))
                            + (use.variant() == null ? ""
                                    : " variant " + Scopes.format(use.variant()))
                            + " (no punishment)");
            return new UseOutcome.Recorded(id);
        });
    }

    /**
     * Which uses a reset concerns (S64): those of {@code templates} (null: of any template) on
     * the target's own victim rows, or, when {@code addressShared}, on every victim an
     * {@code address-shared} ladder counts for it (its alts and addresses too).
     */
    public record ResetSelection(@Nullable Set<String> templates, boolean addressShared) {

        public ResetSelection {
            templates = templates == null ? null : Set.copyOf(templates);
        }
    }

    /** How many uses a reset deletes, and on how many victim rows. */
    public record ResetCount(int uses, int victims) {
    }

    /** What {@link #resetUses} would delete now. */
    public CompletableFuture<ResetCount> resetCount(final Victim target,
            final List<ResetSelection> selections, final Instant now) {
        return this.storage.read(dsl -> count(dsl, this.resetCondition(target, selections, now)));
    }

    /**
     * Forgets the uses the selections name for the target, so the ladders they count for start
     * over; audited on the target ({@link #victimSubject}: never its address). {@code template} is
     * only the audit's wording: one template, or null for every template.
     */
    public CompletableFuture<ResetCount> resetUses(final Victim target,
            final @Nullable String template, final List<ResetSelection> selections,
            final Actor actor, final Instant at) {
        return this.storage.write(configuration -> {
            final DSLContext dsl = configuration.dsl();
            final Condition which = this.resetCondition(target, selections, at);
            final ResetCount found = count(dsl, which);
            final int deleted = dsl.deleteFrom(SakuraTables.TEMPLATE_USES).where(which)
                    .execute();
            final ResetCount done = new ResetCount(deleted, found.victims());
            final Subject subject = victimSubject(dsl, target);
            AuditRepository.append(dsl, at, actor, AuditAction.RESET, subject.type(),
                    subject.id(), "template uses of "
                            + (template == null ? "every template" : template) + ": "
                            + done.uses() + " of " + done.victims() + " victims",
                    null);
            return done;
        });
    }

    private Condition resetCondition(final Victim target, final List<ResetSelection> selections,
            final Instant now) {
        final SbTemplateUses u = SakuraTables.TEMPLATE_USES;
        Condition any = DSL.falseCondition();
        for (final ResetSelection selection : selections) {
            final Condition victims = u.VICTIM_ID.in(victims(target, selection.addressShared(),
                    now, this.policy));
            any = any.or(selection.templates() == null ? victims
                    : victims.and(u.TEMPLATE.in(selection.templates())));
        }
        return any;
    }

    private static ResetCount count(final DSLContext dsl, final Condition which) {
        final SbTemplateUses u = SakuraTables.TEMPLATE_USES;
        final Record2<Integer, Integer> row = dsl.select(DSL.count(),
                        DSL.countDistinct(u.VICTIM_ID))
                .from(u).where(which).fetchSingle();
        return new ResetCount(row.value1(), row.value2());
    }

    /** An audit subject. */
    record Subject(SubjectType type, String id) {
    }

    /**
     * A victim as an audit subject, never with its address (S64): {@code PLAYER} and the UUID for
     * a victim with one (player, composite, erased composite); {@code ADDRESS_HASH} and the
     * address hash {@code sb_victims} stores (hex) for an address; {@code VICTIM} and the
     * {@code sb_victims} id for a range or an erased address. Erasing a player keeps the hashes
     * of {@code sb_victims} (D28), so the subject needs no redaction.
     */
    static Subject victimSubject(final DSLContext dsl, final Victim victim) {
        return switch (victim) {
            case PlayerVictim player -> new Subject(SubjectType.PLAYER, player.uuid().toString());
            case CompositeVictim composite -> new Subject(SubjectType.PLAYER,
                    composite.uuid().toString());
            case ErasedAddressVictim erased when erased.uuid().isPresent() ->
                    new Subject(SubjectType.PLAYER, erased.uuid().get().toString());
            case AddressVictim address -> new Subject(SubjectType.ADDRESS_HASH,
                    HexFormat.of().formatHex(AddressHashes.of(address.address())));
            case AddressRangeVictim range -> new Subject(SubjectType.VICTIM,
                    Long.toString(VictimRepository.resolve(dsl, range)));
            case ErasedAddressVictim erased -> new Subject(SubjectType.VICTIM,
                    Long.toString(VictimRepository.resolve(dsl, erased)));
        };
    }

    static long insertUse(final DSLContext dsl, final NewTemplateUse use,
            final @Nullable Long punishmentId) {
        final SbTemplateUses u = SakuraTables.TEMPLATE_USES;
        final StoredOperator operator = StoredOperator.of(use.actor().operator());
        return dsl.insertInto(u)
                .set(u.TEMPLATE, use.template())
                .set(u.TEMPLATE_VARIANT_SCOPE_ID, use.variant() == null ? null
                        : ScopeRepository.resolve(dsl, use.variant()))
                .set(u.STEP, use.step() == null ? null : use.step().shortValue())
                .set(u.VICTIM_ID, VictimRepository.resolve(dsl, use.victim()))
                .set(u.PUNISHMENT_ID, punishmentId)
                .set(u.OPERATOR_KIND, operator.kind())
                .set(u.OPERATOR_UUID, operator.uuid())
                .set(u.OPERATOR_NAME, operator.name())
                .set(u.SOURCE, use.actor().source())
                .set(u.CREATED_AT, use.createdAt())
                .returningResult(u.ID)
                .fetchSingle()
                .value1();
    }

    private static Select<Record1<Long>> victims(final Victim victim,
            final boolean addressShared, final Instant now, final AltPolicy policy) {
        final SbVictims v = SakuraTables.VICTIMS;
        final UUID uuid = switch (victim) {
            case PlayerVictim player -> player.uuid();
            case CompositeVictim composite -> composite.uuid();
            default -> null;
        };
        final InetAddress address = switch (victim) {
            case AddressVictim single -> single.address();
            case CompositeVictim composite -> composite.address();
            default -> null;
        };
        if (victim instanceof AddressRangeVictim || victim instanceof ErasedAddressVictim) {
            return DSL.select(v.ID).from(v).where(StoredVictim.of(victim).matches(v));
        }
        if (addressShared) {
            return ApplicabilityConditions.victimIds(Applicability.of(uuid, address,
                    AddressStrictness.STRICT), policy, now);
        }
        if (uuid != null) {
            return DSL.select(v.ID).from(v)
                    .where(v.UUID.eq(uuid))
                    .and(v.KIND.in(VictimKind.PLAYER, VictimKind.COMPOSITE));
        }
        return DSL.select(v.ID).from(v)
                .where(v.ADDRESS_HASH.eq(AddressHashes.of(address)))
                .and(v.KIND.in(VictimKind.ADDRESS, VictimKind.COMPOSITE));
    }

    private static List<StoredTemplate> load(final DSLContext dsl) {
        return dsl.selectFrom(SakuraTables.TEMPLATES)
                .orderBy(SakuraTables.TEMPLATES.NAME, SakuraTables.TEMPLATES.VARIANT_SCOPE_ID)
                .fetch()
                .stream()
                .map(row -> assemble(dsl, row))
                .toList();
    }

    private static Optional<SbTemplatesRecord> row(final DSLContext dsl, final int variantId,
            final String name) {
        return dsl.selectFrom(SakuraTables.TEMPLATES)
                .where(SakuraTables.TEMPLATES.VARIANT_SCOPE_ID.eq(variantId))
                .and(SakuraTables.TEMPLATES.NAME.eq(name))
                .fetchOptional();
    }

    /** {@code lock}: held until the transaction ends, so the global row can't go meanwhile. */
    private static Optional<SbTemplatesRecord> globalRow(final DSLContext dsl, final String name,
            final boolean lock) {
        final SbTemplates t = SakuraTables.TEMPLATES;
        final var select = dsl.selectFrom(t)
                .where(t.VARIANT_SCOPE_ID.eq(DSL.select(SakuraTables.SCOPES.ID)
                        .from(SakuraTables.SCOPES)
                        .where(ScopeRepository.matches(GlobalScope.INSTANCE))))
                .and(t.NAME.eq(name));
        return lock ? select.forUpdate().fetchOptional() : select.fetchOptional();
    }

    /** The scopes of a name's variants, by their text. */
    private static List<Scope> variants(final DSLContext dsl, final String name) {
        final SbTemplates t = SakuraTables.TEMPLATES;
        return dsl.select(SakuraTables.SCOPES.fields()).from(t)
                .join(SakuraTables.SCOPES).on(SakuraTables.SCOPES.ID.eq(t.VARIANT_SCOPE_ID))
                .where(t.NAME.eq(name))
                .and(SakuraTables.SCOPES.KIND.ne(ScopeKind.GLOBAL))
                .fetch()
                .stream()
                .map(row -> ScopeRepository.scope(row.into(SakuraTables.SCOPES)))
                .sorted(Comparator.comparing(Scopes::format))
                .toList();
    }

    private static StoredTemplate assemble(final DSLContext dsl, final SbTemplatesRecord row) {
        final SbTemplateSteps s = SakuraTables.TEMPLATE_STEPS;
        final List<TemplateStep> steps = dsl.selectFrom(s)
                .where(s.TEMPLATE_ID.eq(row.id()))
                .orderBy(s.POSITION)
                .fetch()
                .stream()
                .map(step -> TemplateStep.of(step.type(),
                        step.durationMode().duration(step.durationMs()), step.reason(),
                        step.message(), split(step.actions())))
                .toList();
        final SbTemplateGroupMembers m = SakuraTables.TEMPLATE_GROUP_MEMBERS;
        final SbTemplateGroups g = SakuraTables.TEMPLATE_GROUPS;
        final Map<String, Integer> groups = new LinkedHashMap<>();
        dsl.select(g.NAME, m.WEIGHT).from(m)
                .join(g).on(g.ID.eq(m.GROUP_ID))
                .where(m.TEMPLATE_ID.eq(row.id()))
                .orderBy(g.NAME)
                .fetch()
                .forEach(member -> groups.put(member.value1(), member.value2().intValue()));
        final Scope scope = row.scopeId() == null ? null : scopeById(dsl, row.scopeId());
        final Scope variant = scopeById(dsl, row.variantScopeId());
        return new StoredTemplate(TemplateBuilder.of(row.name(), row.kind())
                .ip(row.ip())
                .shadow(row.shadow())
                .scope(scope)
                .reason(row.reason())
                .duration(row.durationMode().duration(row.durationMs()))
                .steps(steps)
                .expiry(row.ladderExpiryMs())
                .addressShared(row.addressShared())
                .permissionRequired(row.permissionRequired())
                .actions(split(row.actions()))
                .groups(groups)
                .message(row.message())
                .display(row.display())
                .variant(variant)
                .build(), row.version());
    }

    private static Scope scopeById(final DSLContext dsl, final int id) {
        return ScopeRepository.scope(dsl.selectFrom(SakuraTables.SCOPES)
                .where(SakuraTables.SCOPES.ID.eq(id))
                .fetchSingle());
    }

    private static Map<Field<?>, @Nullable Object> values(final DSLContext dsl,
            final TemplateInfo template, final Instant at) {
        final SbTemplates t = SakuraTables.TEMPLATES;
        final Map<Field<?>, @Nullable Object> values = new HashMap<>();
        final TemplateDuration duration = template instanceof PresetTemplate preset
                ? preset.duration() : TemplateDuration.open();
        values.put(t.KIND, TemplateKind.of(template));
        values.put(t.DURATION_MODE, DurationMode.of(duration));
        values.put(t.DURATION_MS, duration.fixedLength().orElse(null));
        values.put(t.IP, template.ip());
        values.put(t.SHADOW, template.shadow());
        values.put(t.SCOPE_ID, template.scope()
                .map(scope -> ScopeRepository.resolve(dsl, scope)).orElse(null));
        values.put(t.REASON, template.reason());
        values.put(t.ADDRESS_SHARED, template instanceof LadderTemplate ladder
                && ladder.addressShared());
        values.put(t.PERMISSION_REQUIRED, template.permissionRequired());
        values.put(t.ACTIONS, join(template.actions()));
        values.put(t.MESSAGE, template.message().orElse(null));
        values.put(t.DISPLAY, template.display());
        values.put(t.UPDATED_AT, at);
        values.put(t.LADDER_EXPIRY_MS, template instanceof LadderTemplate ladder
                ? ladder.expiry().orElse(null) : null);
        return values;
    }

    private static void writeChildren(final DSLContext dsl, final int id,
            final TemplateInfo template) {
        final SbTemplateSteps s = SakuraTables.TEMPLATE_STEPS;
        final List<TemplateStep> steps = template instanceof LadderTemplate ladder
                ? ladder.steps() : List.of();
        for (int i = 0; i < steps.size(); i++) {
            final TemplateStep step = steps.get(i);
            dsl.insertInto(s)
                    .set(s.TEMPLATE_ID, id)
                    .set(s.POSITION, (short) i)
                    .set(s.TYPE, step.type().orElse(null))
                    .set(s.REASON, step.reason().orElse(null))
                    .set(s.MESSAGE, step.message().orElse(null))
                    .set(s.DURATION_MODE, DurationMode.of(step.duration()))
                    .set(s.DURATION_MS, step.duration().fixedLength().orElse(null))
                    .set(s.ACTIONS, join(step.actions()))
                    .execute();
        }
        final SbTemplateGroups g = SakuraTables.TEMPLATE_GROUPS;
        final SbTemplateGroupMembers m = SakuraTables.TEMPLATE_GROUP_MEMBERS;
        for (final Map.Entry<String, Integer> group : template.groups().entrySet()) {
            dsl.insertInto(g)
                    .set(g.NAME, group.getKey())
                    .set(g.VERSION, 1L)
                    .onConflictDoNothing()
                    .execute();
            final int groupId = dsl.select(g.ID).from(g)
                    .where(g.NAME.eq(group.getKey()))
                    .fetchSingle(g.ID);
            dsl.insertInto(m)
                    .set(m.GROUP_ID, groupId)
                    .set(m.TEMPLATE_ID, id)
                    .set(m.WEIGHT, group.getValue().shortValue())
                    .execute();
        }
    }

    private static void deleteChildren(final DSLContext dsl, final int id) {
        dsl.deleteFrom(SakuraTables.TEMPLATE_STEPS)
                .where(SakuraTables.TEMPLATE_STEPS.TEMPLATE_ID.eq(id))
                .execute();
        final SbTemplateGroupMembers m = SakuraTables.TEMPLATE_GROUP_MEMBERS;
        dsl.deleteFrom(m).where(m.TEMPLATE_ID.eq(id)).execute();
        // A group exists only through its members.
        final SbTemplateGroups g = SakuraTables.TEMPLATE_GROUPS;
        dsl.deleteFrom(g)
                .where(g.ID.notIn(DSL.select(m.GROUP_ID).from(m)))
                .execute();
    }

    private static String join(final List<String> values) {
        return String.join(SEPARATOR, values);
    }

    private static List<String> split(final String text) {
        return text.isEmpty() ? List.of()
                : Arrays.stream(text.split(SEPARATOR)).collect(Collectors.toUnmodifiableList());
    }
}
