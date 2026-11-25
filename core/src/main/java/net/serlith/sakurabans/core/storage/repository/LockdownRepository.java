package net.serlith.sakurabans.core.storage.repository;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import net.serlith.sakurabans.api.model.Scope;
import net.serlith.sakurabans.core.model.Lockdown;
import net.serlith.sakurabans.core.storage.StorageService;
import net.serlith.sakurabans.core.storage.dbo.SakuraTables;
import net.serlith.sakurabans.core.storage.dbo.tables.SbLockdowns;
import org.jooq.DSLContext;

public final class LockdownRepository {

    private final StorageService storage;

    public LockdownRepository(final StorageService storage) {
        this.storage = storage;
    }

    public CompletableFuture<List<Lockdown>> active(final Instant now) {
        final SbLockdowns lockdowns = SakuraTables.LOCKDOWNS;
        return this.storage.write(configuration -> {
            final DSLContext dsl = configuration.dsl();
            dsl.deleteFrom(lockdowns).where(lockdowns.ENDS_AT.le(now)).execute();
            return dsl.select(lockdowns.fields())
                    .select(SakuraTables.SCOPES.fields())
                    .from(lockdowns)
                    .join(SakuraTables.SCOPES).on(SakuraTables.SCOPES.ID.eq(lockdowns.SCOPE_ID))
                    .fetch(row -> {
                        final var lockdown = row.into(lockdowns);
                        return new Lockdown(ScopeRepository.scope(row.into(SakuraTables.SCOPES)),
                                lockdown.reason(), StoredOperator.actor(
                                        lockdown.startedByKind(), lockdown.startedByUuid(),
                                        lockdown.startedByName(), lockdown.startedBySource()),
                                lockdown.startedAt(), lockdown.endsAt());
                    });
        });
    }

    public CompletableFuture<Void> start(final Lockdown lockdown) {
        final SbLockdowns lockdowns = SakuraTables.LOCKDOWNS;
        return this.storage.writeRetryingConflicts(configuration -> {
            final DSLContext dsl = configuration.dsl();
            final int scopeId = ScopeRepository.resolve(dsl, lockdown.scope());
            dsl.deleteFrom(lockdowns).where(lockdowns.SCOPE_ID.eq(scopeId)).execute();
            final StoredOperator by = StoredOperator.of(lockdown.startedBy().operator());
            dsl.insertInto(lockdowns)
                    .set(lockdowns.SCOPE_ID, scopeId)
                    .set(lockdowns.REASON, lockdown.reason())
                    .set(lockdowns.STARTED_BY_KIND, by.kind())
                    .set(lockdowns.STARTED_BY_UUID, by.uuid())
                    .set(lockdowns.STARTED_BY_NAME, by.name())
                    .set(lockdowns.STARTED_BY_SOURCE, lockdown.startedBy().source())
                    .set(lockdowns.STARTED_AT, lockdown.startedAt())
                    .set(lockdowns.ENDS_AT, lockdown.endsAt())
                    .execute();
            return null;
        });
    }

    public CompletableFuture<Boolean> end(final Scope scope) {
        final SbLockdowns lockdowns = SakuraTables.LOCKDOWNS;
        return this.storage.write(configuration -> {
            final DSLContext dsl = configuration.dsl();
            return ScopeRepository.lookup(dsl, scope)
                    .map(id -> dsl.deleteFrom(lockdowns).where(lockdowns.SCOPE_ID.eq(id))
                            .execute() > 0)
                    .orElse(false);
        });
    }
}
