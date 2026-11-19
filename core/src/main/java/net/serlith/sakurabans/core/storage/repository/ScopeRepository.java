package net.serlith.sakurabans.core.storage.repository;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;
import net.serlith.sakurabans.api.model.CategoryScope;
import net.serlith.sakurabans.api.model.GlobalScope;
import net.serlith.sakurabans.api.model.Scope;
import net.serlith.sakurabans.api.model.ServerScope;
import net.serlith.sakurabans.core.storage.StorageService;
import net.serlith.sakurabans.core.storage.dbo.SakuraTables;
import net.serlith.sakurabans.core.storage.dbo.ScopeKind;
import net.serlith.sakurabans.core.storage.dbo.records.SbScopesRecord;
import org.jooq.Condition;
import org.jooq.DSLContext;

public final class ScopeRepository {

    static final String GLOBAL_VALUE = "*";

    private final StorageService storage;

    public ScopeRepository(final StorageService storage) {
        this.storage = storage;
    }

    public CompletableFuture<Integer> id(final Scope scope) {
        return this.storage.writeRetryingConflicts(
                configuration -> resolve(configuration.dsl(), scope));
    }

    public CompletableFuture<Optional<Integer>> find(final Scope scope) {
        return this.storage.read(dsl -> lookup(dsl, scope));
    }

    public CompletableFuture<Optional<Scope>> byId(final int id) {
        return this.storage.read(dsl -> dsl.selectFrom(SakuraTables.SCOPES)
                .where(SakuraTables.SCOPES.ID.eq(id))
                .fetchOptional()
                .map(ScopeRepository::scope));
    }

    public CompletableFuture<Map<Integer, Scope>> all() {
        return this.storage.read(dsl -> dsl.selectFrom(SakuraTables.SCOPES).fetch().stream()
                .collect(Collectors.toUnmodifiableMap(SbScopesRecord::id,
                        ScopeRepository::scope)));
    }

    /**
     * Insert-or-select, safe when instances race, see {@link VictimRepository}.
     */
    static int resolve(final DSLContext dsl, final Scope scope) {
        final Optional<Integer> existing = lookup(dsl, scope);
        if (existing.isPresent()) {
            return existing.get();
        }
        dsl.insertInto(SakuraTables.SCOPES)
                .set(SakuraTables.SCOPES.KIND, kind(scope))
                .set(SakuraTables.SCOPES.VALUE, value(scope))
                .onConflictDoNothing()
                .execute();
        return lookup(dsl, scope).orElseThrow(
                () -> new IllegalStateException("the scope row vanished: " + scope));
    }

    static Optional<Integer> lookup(final DSLContext dsl, final Scope scope) {
        return dsl.select(SakuraTables.SCOPES.ID)
                .from(SakuraTables.SCOPES)
                .where(matches(scope))
                .fetchOptional(SakuraTables.SCOPES.ID);
    }

    static Condition matches(final Scope scope) {
        return SakuraTables.SCOPES.KIND.eq(kind(scope))
                .and(SakuraTables.SCOPES.VALUE.eq(value(scope)));
    }

    static Scope scope(final SbScopesRecord row) {
        return scope(row.kind(), row.value());
    }

    static Scope scope(final ScopeKind kind, final String value) {
        return switch (kind) {
            case GLOBAL -> GlobalScope.INSTANCE;
            case SERVER -> ServerScope.of(value);
            case CATEGORY -> CategoryScope.of(value);
        };
    }

    private static ScopeKind kind(final Scope scope) {
        return switch (scope) {
            case GlobalScope global -> ScopeKind.GLOBAL;
            case ServerScope server -> ScopeKind.SERVER;
            case CategoryScope category -> ScopeKind.CATEGORY;
        };
    }

    private static String value(final Scope scope) {
        return switch (scope) {
            case GlobalScope global -> GLOBAL_VALUE;
            case ServerScope server -> server.server();
            case CategoryScope category -> category.category();
        };
    }
}
