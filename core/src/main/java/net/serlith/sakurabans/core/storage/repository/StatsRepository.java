package net.serlith.sakurabans.core.storage.repository;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.BiConsumer;
import net.serlith.sakurabans.api.model.EndKind;
import net.serlith.sakurabans.api.model.Operator;
import net.serlith.sakurabans.api.punish.PunishmentQuery;
import net.serlith.sakurabans.core.storage.StorageService;
import net.serlith.sakurabans.core.storage.dbo.SakuraTables;
import net.serlith.sakurabans.core.storage.dbo.VictimKind;
import net.serlith.sakurabans.core.storage.dbo.tables.SbPunishments;
import org.jooq.Condition;
import org.jooq.DSLContext;
import org.jooq.impl.DSL;

public final class StatsRepository {

    private final StorageService storage;
    private final AltPolicy policy;

    public StatsRepository(final StorageService storage, final AltPolicy policy) {
        this.storage = storage;
        this.policy = policy;
    }

    static Condition naming(final UUID uuid) {
        final SbPunishments p = SakuraTables.PUNISHMENTS;
        return p.VICTIM_ID.in(DSL.select(SakuraTables.VICTIMS.ID).from(SakuraTables.VICTIMS)
                .where(SakuraTables.VICTIMS.UUID.eq(uuid))
                .and(SakuraTables.VICTIMS.KIND.in(VictimKind.PLAYER, VictimKind.COMPOSITE)));
    }

    public record AccountCounts(Map<String, Long> perKind, long voided) {

        public AccountCounts {
            perKind = Map.copyOf(perKind);
        }
    }

    public CompletableFuture<AccountCounts> account(final UUID uuid) {
        final SbPunishments p = SakuraTables.PUNISHMENTS;
        return this.storage.read(dsl -> new AccountCounts(
                perKind(dsl, naming(uuid).and(p.VOIDED_AT.isNull())),
                dsl.fetchCount(p, naming(uuid).and(p.VOIDED_AT.isNotNull()))));
    }

    public record StaffCounts(Map<String, Long> issued, Map<String, Long> revoked,
            Map<String, Long> voided) {

        public StaffCounts {
            issued = Map.copyOf(issued);
            revoked = Map.copyOf(revoked);
            voided = Map.copyOf(voided);
        }
    }

    public CompletableFuture<StaffCounts> staff(final Operator operator, final Instant from,
            final Instant to) {
        final SbPunishments p = SakuraTables.PUNISHMENTS;
        return this.storage.read(dsl -> new StaffCounts(
                perKind(dsl, StoredOperator.identity(operator, p.OPERATOR_KIND, p.OPERATOR_UUID,
                        p.OPERATOR_NAME).and(p.VOIDED_AT.isNull())
                        .and(p.CREATED_AT.ge(from)).and(p.CREATED_AT.lt(to))),
                perKind(dsl, StoredOperator.identity(operator, p.REVOKER_KIND, p.REVOKER_UUID,
                        p.REVOKER_NAME).and(p.END_KIND.eq(EndKind.REVOKED))
                        .and(p.ENDED_AT.ge(from)).and(p.ENDED_AT.lt(to))),
                perKind(dsl, StoredOperator.identity(operator, p.VOIDER_KIND, p.VOIDER_UUID,
                        p.VOIDER_NAME).and(p.VOIDED_AT.ge(from)).and(p.VOIDED_AT.lt(to)))));
    }

    public CompletableFuture<Void> created(final PunishmentQuery query, final Instant now,
            final BiConsumer<Instant, String> each) {
        final SbPunishments p = SakuraTables.PUNISHMENTS;
        return this.storage.read(dsl -> {
            final List<Condition> conditions =
                    PunishmentRepository.conditions(query, now, this.policy);
            try (var rows = dsl.select(p.CREATED_AT, p.KIND)
                    .from(p.join(SakuraTables.VICTIMS)
                            .on(SakuraTables.VICTIMS.ID.eq(p.VICTIM_ID))
                            .join(SakuraTables.SCOPES)
                            .on(SakuraTables.SCOPES.ID.eq(p.SCOPE_ID)))
                    .where(conditions)
                    .fetchSize(1_000)
                    .fetchStream()) {
                rows.forEach(row -> each.accept(row.value1(), row.value2()));
            }
            return null;
        });
    }

    private static Map<String, Long> perKind(final DSLContext dsl, final Condition condition) {
        final SbPunishments p = SakuraTables.PUNISHMENTS;
        final Map<String, Long> counts = new HashMap<>();
        dsl.select(p.KIND, DSL.count()).from(p).where(condition).groupBy(p.KIND).fetch()
                .forEach(row -> counts.put(row.value1(), row.value2().longValue()));
        return counts;
    }

}
