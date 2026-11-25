package net.serlith.sakurabans.core.storage.repository;

import java.net.InetAddress;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;
import net.serlith.sakurabans.api.audit.AuditAction;
import net.serlith.sakurabans.api.audit.SubjectType;
import net.serlith.sakurabans.api.model.Actor;
import net.serlith.sakurabans.api.model.AddressRangeVictim;
import net.serlith.sakurabans.api.model.AddressStrictness;
import net.serlith.sakurabans.api.model.AddressVictim;
import net.serlith.sakurabans.api.model.CompositeVictim;
import net.serlith.sakurabans.api.model.ErasedAddressVictim;
import net.serlith.sakurabans.api.model.PlayerVictim;
import net.serlith.sakurabans.api.model.PunishmentType;
import net.serlith.sakurabans.api.model.Victim;
import net.serlith.sakurabans.api.punish.PunishmentQuery.Applicability;
import net.serlith.sakurabans.api.template.TemplateStep;
import net.serlith.sakurabans.core.model.SakuraTemplateInfo;
import net.serlith.sakurabans.core.model.TemplateDefinition;
import net.serlith.sakurabans.core.storage.StorageService;
import net.serlith.sakurabans.core.storage.dbo.SakuraTables;
import net.serlith.sakurabans.core.storage.dbo.VictimKind;
import net.serlith.sakurabans.core.storage.dbo.records.SbTemplatesRecord;
import net.serlith.sakurabans.core.storage.dbo.tables.SbPunishments;
import net.serlith.sakurabans.core.storage.dbo.tables.SbTemplateGroupMembers;
import net.serlith.sakurabans.core.storage.dbo.tables.SbTemplateGroups;
import net.serlith.sakurabans.core.storage.dbo.tables.SbTemplateSteps;
import net.serlith.sakurabans.core.storage.dbo.tables.SbTemplates;
import net.serlith.sakurabans.core.storage.dbo.tables.SbVictims;
import net.serlith.sakurabans.core.util.AddressHashes;
import org.jooq.Condition;
import org.jooq.DSLContext;
import org.jooq.Field;
import org.jooq.Record1;
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

    public CompletableFuture<List<SakuraTemplateInfo>> all() {
        return this.storage.read(TemplateRepository::load);
    }

    public CompletableFuture<EntityWrite<SakuraTemplateInfo>> create(
            final TemplateDefinition definition, final PunishmentType type, final Actor actor,
            final Instant at) {
        return this.storage.writeRetryingConflicts(configuration -> {
            final DSLContext dsl = configuration.dsl();
            final Optional<SakuraTemplateInfo> existing = find(dsl, definition.name());
            if (existing.isPresent()) {
                return new EntityWrite.Taken<>(existing.get());
            }
            final SakuraTemplateInfo created = SakuraTemplateInfo.of(definition, type, 1);
            final SbTemplates t = SakuraTables.TEMPLATES;
            final int id = dsl.insertInto(t)
                    .set(t.NAME, created.name())
                    .set(t.CREATED_AT, at)
                    .set(t.VERSION, 1L)
                    .set(values(created, at))
                    .returningResult(t.ID)
                    .fetchSingle()
                    .value1();
            writeChildren(dsl, id, created);
            AuditRepository.append(dsl, at, actor, AuditAction.CREATE, SubjectType.TEMPLATE,
                    created.name(), null, created.summary());
            return new EntityWrite.Done<>(created, null);
        });
    }

    public CompletableFuture<EntityWrite<SakuraTemplateInfo>> update(
            final TemplateDefinition definition, final PunishmentType type,
            final @Nullable Long expectedVersion, final Actor actor, final Instant at) {
        return this.storage.writeRetryingConflicts(configuration -> {
            final DSLContext dsl = configuration.dsl();
            final SbTemplatesRecord row = row(dsl, definition.name()).orElse(null);
            if (row == null) {
                return new EntityWrite.NotFound<>();
            }
            if (expectedVersion != null && row.version() != expectedVersion.longValue()) {
                return new EntityWrite.Conflict<>(row.version());
            }
            final SakuraTemplateInfo previous = assemble(dsl, row);
            final SakuraTemplateInfo next =
                    SakuraTemplateInfo.of(definition, type, row.version() + 1);
            if (sameValues(previous, next)) {
                return new EntityWrite.Unchanged<>(previous);
            }
            final SbTemplates t = SakuraTables.TEMPLATES;
            final int updated = dsl.update(t)
                    .set(values(next, at))
                    .set(t.VERSION, next.version())
                    .where(t.ID.eq(row.id()))
                    .and(t.VERSION.eq(row.version()))
                    .execute();
            if (updated == 0) {
                return new EntityWrite.Conflict<>(row(dsl, definition.name())
                        .map(SbTemplatesRecord::version).orElse(row.version()));
            }
            deleteChildren(dsl, row.id());
            writeChildren(dsl, row.id(), next);
            AuditRepository.append(dsl, at, actor, AuditAction.UPDATE, SubjectType.TEMPLATE,
                    next.name(), previous.summary(), next.summary());
            return new EntityWrite.Done<>(next, previous);
        });
    }

    public CompletableFuture<EntityWrite<SakuraTemplateInfo>> delete(final String name,
            final long expectedVersion, final Actor actor, final Instant at) {
        return this.storage.write(configuration -> {
            final DSLContext dsl = configuration.dsl();
            final SbTemplatesRecord row = row(dsl, name).orElse(null);
            if (row == null) {
                return new EntityWrite.NotFound<>();
            }
            if (row.version() != expectedVersion) {
                return new EntityWrite.Conflict<>(row.version());
            }
            final SakuraTemplateInfo previous = assemble(dsl, row);
            deleteChildren(dsl, row.id());
            dsl.deleteFrom(SakuraTables.TEMPLATES)
                    .where(SakuraTables.TEMPLATES.ID.eq(row.id()))
                    .execute();
            AuditRepository.append(dsl, at, actor, AuditAction.DELETE, SubjectType.TEMPLATE,
                    name, previous.summary(), null);
            return new EntityWrite.Done<>(previous, previous);
        });
    }

    public CompletableFuture<Map<String, Long>> history(final Set<String> templates,
            final Victim victim, final boolean addressShared, final @Nullable Instant since,
            final Instant now) {
        if (templates.isEmpty()) {
            return CompletableFuture.completedFuture(Map.of());
        }
        final SbPunishments p = SakuraTables.PUNISHMENTS;
        return this.storage.read(dsl -> {
            final List<Condition> conditions = new ArrayList<>();
            conditions.add(p.TEMPLATE.in(templates));
            conditions.add(p.VOIDED_AT.isNull());
            conditions.add(p.VICTIM_ID.in(this.victims(victim, addressShared, now)));
            if (since != null) {
                conditions.add(p.CREATED_AT.ge(since));
            }
            final Map<String, Long> counts = new HashMap<>();
            dsl.select(p.TEMPLATE, DSL.count()).from(p)
                    .where(conditions)
                    .groupBy(p.TEMPLATE)
                    .fetch()
                    .forEach(row -> counts.put(row.value1(), row.value2().longValue()));
            return Map.copyOf(counts);
        });
    }

    private Select<Record1<Long>> victims(final Victim victim, final boolean addressShared,
            final Instant now) {
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
                    AddressStrictness.STRICT), this.policy, now);
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

    private static List<SakuraTemplateInfo> load(final DSLContext dsl) {
        return dsl.selectFrom(SakuraTables.TEMPLATES)
                .orderBy(SakuraTables.TEMPLATES.NAME)
                .fetch()
                .stream()
                .map(row -> assemble(dsl, row))
                .toList();
    }

    private static Optional<SakuraTemplateInfo> find(final DSLContext dsl, final String name) {
        return row(dsl, name).map(row -> assemble(dsl, row));
    }

    private static Optional<SbTemplatesRecord> row(final DSLContext dsl, final String name) {
        return dsl.selectFrom(SakuraTables.TEMPLATES)
                .where(SakuraTables.TEMPLATES.NAME.eq(name))
                .fetchOptional();
    }

    private static SakuraTemplateInfo assemble(final DSLContext dsl, final SbTemplatesRecord row) {
        final SbTemplateSteps s = SakuraTables.TEMPLATE_STEPS;
        final List<TemplateStep> ladder = dsl.selectFrom(s)
                .where(s.TEMPLATE_ID.eq(row.id()))
                .orderBy(s.POSITION)
                .fetch()
                .stream()
                .map(step -> TemplateStep.of(step.durationMs(), split(step.actions())))
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
        return new SakuraTemplateInfo(row.name(), row.kind(), row.type(), row.reason(), ladder,
                Set.copyOf(split(row.requiredEvidence())), row.ladderExpiryMs(),
                row.addressShared(), row.permissionRequired(), split(row.actions()), groups,
                row.version());
    }

    private static Map<Field<?>, @Nullable Object> values(final SakuraTemplateInfo template,
            final Instant at) {
        final SbTemplates t = SakuraTables.TEMPLATES;
        final Map<Field<?>, @Nullable Object> values = new HashMap<>();
        values.put(t.KIND, template.kind());
        values.put(t.TYPE, template.type());
        values.put(t.REASON, template.reason());
        values.put(t.REQUIRED_EVIDENCE, join(template.requiredEvidence().stream().sorted()
                .toList()));
        values.put(t.ADDRESS_SHARED, template.addressShared());
        values.put(t.PERMISSION_REQUIRED, template.permissionRequired());
        values.put(t.ACTIONS, join(template.actions()));
        values.put(t.UPDATED_AT, at);
        values.put(t.LADDER_EXPIRY_MS, template.ladderExpiryOrNull());
        return values;
    }

    private static void writeChildren(final DSLContext dsl, final int id,
            final SakuraTemplateInfo template) {
        final SbTemplateSteps s = SakuraTables.TEMPLATE_STEPS;
        for (int i = 0; i < template.ladder().size(); i++) {
            final TemplateStep step = template.ladder().get(i);
            dsl.insertInto(s)
                    .set(s.TEMPLATE_ID, id)
                    .set(s.POSITION, (short) i)
                    .set(s.DURATION_MS, step.duration().orElse(null))
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

    private static boolean sameValues(final SakuraTemplateInfo a, final SakuraTemplateInfo b) {
        return a.summary().equals(b.summary()) && a.type() == b.type();
    }

    private static String join(final List<String> values) {
        return String.join(SEPARATOR, values);
    }

    private static List<String> split(final String text) {
        return text.isEmpty() ? List.of()
                : Arrays.stream(text.split(SEPARATOR)).collect(Collectors.toUnmodifiableList());
    }
}
