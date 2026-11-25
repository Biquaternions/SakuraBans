package net.serlith.sakurabans.core.storage.repository;

import java.time.Instant;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;
import net.serlith.sakurabans.api.audit.AuditAction;
import net.serlith.sakurabans.api.audit.SubjectType;
import net.serlith.sakurabans.api.kind.KindDefinition;
import net.serlith.sakurabans.api.model.Actor;
import net.serlith.sakurabans.api.model.Scope;
import net.serlith.sakurabans.core.model.SakuraKind;
import net.serlith.sakurabans.core.storage.StorageService;
import net.serlith.sakurabans.core.storage.dbo.SakuraTables;
import net.serlith.sakurabans.core.storage.dbo.records.SbKindsRecord;
import net.serlith.sakurabans.core.storage.dbo.tables.SbKinds;
import org.jooq.DSLContext;
import org.jspecify.annotations.Nullable;

public final class KindRepository {

    private static final String SEPARATOR = "\n";

    private final StorageService storage;

    public KindRepository(final StorageService storage) {
        this.storage = storage;
    }

    public CompletableFuture<List<SakuraKind>> all() {
        return this.storage.read(dsl -> {
            final Map<Integer, Scope> scopes = dsl.selectFrom(SakuraTables.SCOPES).fetch()
                    .stream().collect(Collectors.toMap(row -> row.id(), ScopeRepository::scope));
            return dsl.selectFrom(SakuraTables.KINDS)
                    .orderBy(SakuraTables.KINDS.ID)
                    .fetch()
                    .stream()
                    .map(row -> kind(row, row.defaultScopeId() == null ? null
                            : scopes.get(row.defaultScopeId())))
                    .toList();
        });
    }

    public CompletableFuture<Integer> seed(final Collection<SakuraKind> builtIn,
            final Instant at) {
        return this.storage.writeRetryingConflicts(configuration -> {
            final DSLContext dsl = configuration.dsl();
            int inserted = 0;
            for (final SakuraKind kind : builtIn) {
                if (row(dsl, kind.id()).isEmpty()) {
                    insert(dsl, kind, at);
                    inserted++;
                }
            }
            return inserted;
        });
    }

    public CompletableFuture<EntityWrite<SakuraKind>> create(final KindDefinition definition,
            final Actor actor, final Instant at) {
        return this.storage.writeRetryingConflicts(configuration -> {
            final DSLContext dsl = configuration.dsl();
            final Optional<SbKindsRecord> existing = row(dsl, definition.id());
            if (existing.isPresent()) {
                return new EntityWrite.Taken<>(assemble(dsl, existing.get()));
            }
            final SakuraKind created = SakuraKind.of(definition, false, 1);
            insert(dsl, created, at);
            AuditRepository.append(dsl, at, actor, AuditAction.CREATE, SubjectType.KIND,
                    created.id(), null, created.summary());
            return new EntityWrite.Done<>(created, null);
        });
    }

    public CompletableFuture<EntityWrite<SakuraKind>> update(final KindDefinition definition,
            final long expectedVersion, final Actor actor, final Instant at) {
        return this.storage.writeRetryingConflicts(configuration -> {
            final DSLContext dsl = configuration.dsl();
            final SbKindsRecord row = row(dsl, definition.id()).orElse(null);
            if (row == null) {
                return new EntityWrite.NotFound<>();
            }
            if (row.version() != expectedVersion) {
                return new EntityWrite.Conflict<>(row.version());
            }
            if (row.baseType() != definition.baseType()) {
                return new EntityWrite.Refused<>("baseType", "the base type of a kind can't "
                        + "change (it is " + row.baseType() + ")");
            }
            final SakuraKind previous = assemble(dsl, row);
            final SakuraKind next = SakuraKind.of(definition, row.builtIn(), row.version() + 1);
            if (previous.summary().equals(next.summary())) {
                return new EntityWrite.Unchanged<>(previous);
            }
            final SbKinds k = SakuraTables.KINDS;
            final int updated = dsl.update(k)
                    .set(k.DISPLAY_NAME, next.displayName())
                    .set(k.SHADOW, next.shadow())
                    .set(k.STRICTNESS, next.strictnessOrNull())
                    .set(k.DEFAULT_SCOPE_ID, scopeId(dsl, next.defaultScopeOrNull()))
                    .set(k.ADDRESS_MODE, next.addressMode())
                    .set(k.PERMANENT_ONLY, next.permanentOnly())
                    .set(k.REQUIRED_EVIDENCE, join(next.requiredEvidence()))
                    .set(k.VERSION, next.version())
                    .set(k.UPDATED_AT, at)
                    .where(k.ID.eq(row.id()))
                    .and(k.VERSION.eq(row.version()))
                    .execute();
            if (updated == 0) {
                return new EntityWrite.Conflict<>(row(dsl, row.id())
                        .map(SbKindsRecord::version).orElse(row.version()));
            }
            AuditRepository.append(dsl, at, actor, AuditAction.UPDATE, SubjectType.KIND,
                    next.id(), previous.summary(), next.summary());
            return new EntityWrite.Done<>(next, previous);
        });
    }

    public CompletableFuture<EntityWrite<SakuraKind>> delete(final String id,
            final long expectedVersion, final Actor actor, final Instant at) {
        return this.storage.write(configuration -> {
            final DSLContext dsl = configuration.dsl();
            final SbKindsRecord row = row(dsl, id).orElse(null);
            if (row == null) {
                return new EntityWrite.NotFound<>();
            }
            if (row.version() != expectedVersion) {
                return new EntityWrite.Conflict<>(row.version());
            }
            if (row.builtIn()) {
                return new EntityWrite.Refused<>("id", "built-in kinds can't be deleted");
            }
            if (dsl.fetchExists(SakuraTables.PUNISHMENTS, SakuraTables.PUNISHMENTS.KIND.eq(id))) {
                return new EntityWrite.Refused<>("id", "punishments of this kind exist");
            }
            if (dsl.fetchExists(SakuraTables.TEMPLATES, SakuraTables.TEMPLATES.KIND.eq(id))) {
                return new EntityWrite.Refused<>("id", "templates use this kind");
            }
            final SakuraKind previous = assemble(dsl, row);
            dsl.deleteFrom(SakuraTables.KINDS).where(SakuraTables.KINDS.ID.eq(id)).execute();
            AuditRepository.append(dsl, at, actor, AuditAction.DELETE, SubjectType.KIND, id,
                    previous.summary(), null);
            return new EntityWrite.Done<>(previous, previous);
        });
    }

    private static void insert(final DSLContext dsl, final SakuraKind kind, final Instant at) {
        final SbKinds k = SakuraTables.KINDS;
        dsl.insertInto(k)
                .set(k.ID, kind.id())
                .set(k.DISPLAY_NAME, kind.displayName())
                .set(k.BASE_TYPE, kind.baseType())
                .set(k.SHADOW, kind.shadow())
                .set(k.STRICTNESS, kind.strictnessOrNull())
                .set(k.DEFAULT_SCOPE_ID, scopeId(dsl, kind.defaultScopeOrNull()))
                .set(k.ADDRESS_MODE, kind.addressMode())
                .set(k.PERMANENT_ONLY, kind.permanentOnly())
                .set(k.REQUIRED_EVIDENCE, join(kind.requiredEvidence()))
                .set(k.BUILT_IN, kind.builtIn())
                .set(k.VERSION, kind.version())
                .set(k.CREATED_AT, at)
                .set(k.UPDATED_AT, at)
                .execute();
    }

    private static @Nullable Integer scopeId(final DSLContext dsl, final @Nullable Scope scope) {
        return scope == null ? null : ScopeRepository.resolve(dsl, scope);
    }

    private static Optional<SbKindsRecord> row(final DSLContext dsl, final String id) {
        return dsl.selectFrom(SakuraTables.KINDS)
                .where(SakuraTables.KINDS.ID.eq(id))
                .fetchOptional();
    }

    private static SakuraKind assemble(final DSLContext dsl, final SbKindsRecord row) {
        final Scope scope = row.defaultScopeId() == null ? null
                : dsl.selectFrom(SakuraTables.SCOPES)
                        .where(SakuraTables.SCOPES.ID.eq(row.defaultScopeId()))
                        .fetchOptional()
                        .map(ScopeRepository::scope)
                        .orElse(null);
        return kind(row, scope);
    }

    private static SakuraKind kind(final SbKindsRecord row, final @Nullable Scope scope) {
        return new SakuraKind(row.id(), row.displayName(), row.baseType(), row.shadow(),
                row.strictness(), scope, row.addressMode(), row.permanentOnly(),
                split(row.requiredEvidence()), row.builtIn(), row.version());
    }

    private static String join(final Set<String> values) {
        return values.stream().sorted().collect(Collectors.joining(SEPARATOR));
    }

    private static Set<String> split(final String text) {
        return text.isEmpty() ? Set.of() : Set.copyOf(Arrays.asList(text.split(SEPARATOR)));
    }

}
