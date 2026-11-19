package net.serlith.sakurabans.core.storage.repository;

import com.google.common.base.Preconditions;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.LongFunction;
import java.util.stream.Collectors;
import net.serlith.sakurabans.api.model.Actor;
import net.serlith.sakurabans.api.model.Evidence;
import net.serlith.sakurabans.api.model.EvidenceRef;
import net.serlith.sakurabans.api.model.Punishment;
import net.serlith.sakurabans.core.model.SakuraEvidence;
import net.serlith.sakurabans.core.model.SakuraPunishment;
import net.serlith.sakurabans.core.model.SakuraPunishmentEnd;
import net.serlith.sakurabans.core.model.SakuraPunishmentVoid;
import net.serlith.sakurabans.core.model.SakuraTemplateRef;
import net.serlith.sakurabans.core.storage.dbo.SakuraTables;
import net.serlith.sakurabans.core.storage.dbo.records.SbPunishmentEvidenceRecord;
import net.serlith.sakurabans.core.storage.dbo.records.SbPunishmentsRecord;
import org.jooq.DSLContext;
import org.jooq.Field;
import org.jooq.Record;
import org.jooq.SelectJoinStep;
import org.jooq.SelectSelectStep;

public final class PunishmentMapper {

    private final LongFunction<String> displayIds;

    public PunishmentMapper(final LongFunction<String> displayIds) {
        this.displayIds = displayIds;
    }

    static SelectJoinStep<Record> select(final DSLContext dsl) {
        final SelectSelectStep<Record> columns = dsl.select(fields());
        return columns.from(SakuraTables.PUNISHMENTS
                .join(SakuraTables.VICTIMS)
                .on(SakuraTables.VICTIMS.ID.eq(SakuraTables.PUNISHMENTS.VICTIM_ID))
                .join(SakuraTables.SCOPES)
                .on(SakuraTables.SCOPES.ID.eq(SakuraTables.PUNISHMENTS.SCOPE_ID))
                .leftJoin(SakuraTables.TEMPLATES)
                .on(SakuraTables.TEMPLATES.ID.eq(SakuraTables.PUNISHMENTS.TEMPLATE_ID)));
    }

    private static List<Field<?>> fields() {
        final List<Field<?>> fields = new ArrayList<>();
        fields.addAll(List.of(SakuraTables.PUNISHMENTS.fields()));
        fields.addAll(List.of(SakuraTables.VICTIMS.fields()));
        fields.addAll(List.of(SakuraTables.SCOPES.fields()));
        fields.add(SakuraTables.TEMPLATES.NAME);
        return fields;
    }

    List<Punishment> map(final DSLContext dsl, final Collection<? extends Record> rows) {
        if (rows.isEmpty()) {
            return List.of();
        }
        final List<Long> ids = rows.stream()
                .map(row -> row.get(SakuraTables.PUNISHMENTS.ID)).toList();
        final Map<Long, List<Evidence>> evidence = dsl
                .selectFrom(SakuraTables.PUNISHMENT_EVIDENCE)
                .where(SakuraTables.PUNISHMENT_EVIDENCE.PUNISHMENT_ID.in(ids))
                .orderBy(SakuraTables.PUNISHMENT_EVIDENCE.ADDED_AT,
                        SakuraTables.PUNISHMENT_EVIDENCE.URI)
                .fetch()
                .stream()
                .collect(Collectors.groupingBy(SbPunishmentEvidenceRecord::punishmentId,
                        Collectors.mapping(PunishmentMapper::evidence, Collectors.toList())));
        return rows.stream()
                .map(row -> this.punishment(row, evidence.getOrDefault(
                        row.get(SakuraTables.PUNISHMENTS.ID), List.of())))
                .toList();
    }

    private Punishment punishment(final Record row, final List<Evidence> evidence) {
        final SbPunishmentsRecord p = row.into(SakuraTables.PUNISHMENTS);
        final String templateName = row.get(SakuraTables.TEMPLATES.NAME);
        final Short templateStep = p.templateStep();
        return new SakuraPunishment(
                p.id(),
                this.displayIds.apply(p.id()),
                p.type(),
                p.kind(),
                p.shadow(),
                p.strictness(),
                VictimRepository.victim(row.into(SakuraTables.VICTIMS)),
                StoredOperator.operator(p.operatorKind(), p.operatorUuid(), p.operatorName()),
                p.source(),
                ScopeRepository.scope(row.into(SakuraTables.SCOPES)),
                p.reason(),
                p.createdAt(),
                p.expiresAt(),
                p.originServer(),
                p.visibility(),
                templateName == null ? null
                        : new SakuraTemplateRef(templateName,
                                templateStep == null ? 0 : templateStep),
                p.endedAt() == null || p.endKind() == null ? null
                        : new SakuraPunishmentEnd(p.endKind(), p.endedAt(),
                                revoker(p).orElse(null), p.revokeReason()),
                p.voidedAt() == null ? null
                        : new SakuraPunishmentVoid(p.voidedAt(), voider(p), p.voidReason()),
                evidence,
                p.version());
    }

    private static Optional<Actor> revoker(final SbPunishmentsRecord p) {
        if (p.revokerKind() == null || p.revokerName() == null || p.revokerSource() == null) {
            return Optional.empty();
        }
        return Optional.of(StoredOperator.actor(p.revokerKind(), p.revokerUuid(),
                p.revokerName(), p.revokerSource()));
    }

    private static Actor voider(final SbPunishmentsRecord p) {
        Preconditions.checkState(p.voiderKind() != null && p.voiderName() != null
                && p.voiderSource() != null, "punishment %s is voided by nobody", p.id());
        return StoredOperator.actor(p.voiderKind(), p.voiderUuid(), p.voiderName(),
                p.voiderSource());
    }

    private static Evidence evidence(final SbPunishmentEvidenceRecord row) {
        return new SakuraEvidence(EvidenceRef.of(row.uri()), StoredOperator.actor(
                row.addedByKind(), row.addedByUuid(), row.addedByName(), row.addedBySource()),
                row.addedAt());
    }
}
