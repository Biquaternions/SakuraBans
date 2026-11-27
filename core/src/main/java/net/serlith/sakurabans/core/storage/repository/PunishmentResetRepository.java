package net.serlith.sakurabans.core.storage.repository;

import com.google.common.base.Preconditions;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import net.serlith.sakurabans.api.model.Actor;
import net.serlith.sakurabans.api.model.EndKind;
import net.serlith.sakurabans.api.model.PunishmentType;
import net.serlith.sakurabans.api.model.Scope;
import net.serlith.sakurabans.core.model.AuditAction;
import net.serlith.sakurabans.core.model.SubjectType;
import net.serlith.sakurabans.core.storage.StorageService;
import net.serlith.sakurabans.core.storage.dbo.SakuraTables;
import net.serlith.sakurabans.core.storage.dbo.tables.SbActivePunishments;
import net.serlith.sakurabans.core.storage.dbo.tables.SbPunishments;
import org.jooq.Condition;
import org.jooq.DSLContext;
import org.jooq.impl.DSL;
import org.jspecify.annotations.Nullable;

public final class PunishmentResetRepository {

    public enum Mode {
        DELETE,
        DEACTIVATE,
        PRUNE
    }

    public record Filter(Set<PunishmentType> types, @Nullable Scope scope) {

        public Filter {
            types = Set.copyOf(types);
        }
    }

    public record Counts(long active, long ended) {

        public long affected(final Mode mode) {
            return switch (mode) {
                case DELETE -> this.active + this.ended;
                case DEACTIVATE -> this.active;
                case PRUNE -> this.ended;
            };
        }
    }

    private final StorageService storage;

    public PunishmentResetRepository(final StorageService storage) {
        this.storage = storage;
    }

    public CompletableFuture<Counts> count(final Filter filter) {
        final SbPunishments p = SakuraTables.PUNISHMENTS;
        final SbActivePunishments active = SakuraTables.ACTIVE_PUNISHMENTS;
        return this.storage.read(dsl -> {
            final long total = dsl.fetchCount(p, matching(filter));
            final long open = dsl.fetchCount(p, matching(filter)
                    .and(p.ID.in(DSL.select(active.PUNISHMENT_ID).from(active))));
            return new Counts(open, total - open);
        });
    }

    public CompletableFuture<Long> run(final Mode mode, final Filter filter, final Actor actor,
            final Instant now, final int batchSize) {
        Preconditions.checkArgument(batchSize > 0, "batchSize must be positive");
        return this.batches(mode, filter, actor, now, batchSize, 0);
    }

    private CompletableFuture<Long> batches(final Mode mode, final Filter filter,
            final Actor actor, final Instant now, final int batchSize, final long done) {
        return this.storage.write(configuration -> this.batch(configuration.dsl(), mode, filter,
                actor, now, batchSize)).thenCompose(count -> count == 0
                        ? this.audit(mode, filter, actor, now, done)
                        : this.batches(mode, filter, actor, now, batchSize, done + count));
    }

    private CompletableFuture<Long> audit(final Mode mode, final Filter filter,
            final Actor actor, final Instant now, final long done) {
        if (done == 0) {
            return CompletableFuture.completedFuture(0L);
        }
        return this.storage.write(configuration -> {
            AuditRepository.append(configuration.dsl(), now, actor, AuditAction.RESET,
                    SubjectType.PUNISHMENT, "all", null, "mode=" + mode + " count=" + done
                            + " types=" + filter.types().stream().map(PunishmentType::name)
                                    .sorted().toList()
                            + " scope=" + (filter.scope() == null ? "all" : filter.scope()));
            return done;
        });
    }

    private int batch(final DSLContext dsl, final Mode mode, final Filter filter,
            final Actor actor, final Instant now, final int batchSize) {
        final SbPunishments p = SakuraTables.PUNISHMENTS;
        final SbActivePunishments active = SakuraTables.ACTIVE_PUNISHMENTS;
        final Condition condition = switch (mode) {
            case DELETE -> matching(filter);
            case DEACTIVATE -> matching(filter)
                    .and(p.ID.in(DSL.select(active.PUNISHMENT_ID).from(active)));
            case PRUNE -> matching(filter)
                    .and(p.ID.notIn(DSL.select(active.PUNISHMENT_ID).from(active)));
        };
        final List<Long> ids = dsl.select(p.ID).from(p).where(condition).orderBy(p.ID.asc())
                .limit(batchSize).fetch(p.ID);
        if (ids.isEmpty()) {
            return 0;
        }
        if (mode == Mode.DEACTIVATE) {
            final StoredOperator by = StoredOperator.of(actor.operator());
            dsl.deleteFrom(active).where(active.PUNISHMENT_ID.in(ids)).execute();
            dsl.update(p)
                    .set(p.ENDED_AT, now)
                    .set(p.END_KIND, EndKind.PRUNED)
                    .set(p.REVOKER_KIND, by.kind())
                    .set(p.REVOKER_UUID, by.uuid())
                    .set(p.REVOKER_NAME, by.name())
                    .set(p.REVOKER_SOURCE, actor.source())
                    .where(p.ID.in(ids))
                    .execute();
            return ids.size();
        }
        dsl.deleteFrom(SakuraTables.PUNISHMENT_EVIDENCE)
                .where(SakuraTables.PUNISHMENT_EVIDENCE.PUNISHMENT_ID.in(ids)).execute();
        dsl.deleteFrom(active).where(active.PUNISHMENT_ID.in(ids)).execute();
        // The uses of the punishments go with them (also by the foreign key's cascade).
        dsl.deleteFrom(SakuraTables.TEMPLATE_USES)
                .where(SakuraTables.TEMPLATE_USES.PUNISHMENT_ID.in(ids)).execute();
        dsl.deleteFrom(p).where(p.ID.in(ids)).execute();
        return ids.size();
    }

    private static Condition matching(final Filter filter) {
        final SbPunishments p = SakuraTables.PUNISHMENTS;
        Condition condition = filter.types().isEmpty()
                ? DSL.falseCondition() : p.TYPE.in(filter.types());
        if (filter.scope() != null) {
            condition = condition.and(p.SCOPE_ID.in(DSL.select(SakuraTables.SCOPES.ID)
                    .from(SakuraTables.SCOPES).where(ScopeRepository.matches(filter.scope()))));
        }
        return condition;
    }
}
