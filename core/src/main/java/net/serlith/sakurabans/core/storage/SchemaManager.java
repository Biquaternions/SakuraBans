package net.serlith.sakurabans.core.storage;

import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import net.kyori.adventure.text.logger.slf4j.ComponentLogger;
import net.serlith.sakurabans.core.storage.dbo.SakuraTables;
import net.serlith.sakurabans.core.storage.dbo.SbTable;
import net.serlith.sakurabans.core.storage.dbo.ScopeKind;
import net.serlith.sakurabans.core.util.BuildConstants;
import org.jooq.Constraint;
import org.jooq.ConstraintForeignKeyOnStep;
import org.jooq.DSLContext;
import org.jooq.ForeignKey;
import org.jooq.Index;
import org.jooq.Query;
import org.jooq.UniqueKey;
import org.jooq.exception.DataAccessException;
import org.jooq.impl.DSL;
import org.jspecify.annotations.Nullable;

public final class SchemaManager {

    static final String VERSION_KEY = "schema_version";
    static final String CREATED_WITH_KEY = "created_with";

    private final SchemaUpdater updater;
    private final ComponentLogger logger;

    public SchemaManager(final ComponentLogger logger) {
        this.updater = new SchemaUpdater();
        this.logger = logger;
    }

    public void prepare(final DSLContext dsl, final @Nullable String schema) {
        if (schema != null) {
            dsl.createSchemaIfNotExists(schema).execute();
        }
        createTable(dsl, SakuraTables.META).execute();

        final Optional<Integer> found = this.version(dsl);
        final int current = SakuraTables.SCHEMA_VERSION;
        if (found.isPresent() && found.get() > current) {
            throw new StorageException("The database was set up by a newer SakuraBans (schema"
                    + " version " + found.get() + "; this version knows up to " + current
                    + "). Update SakuraBans or use another database.");
        }
        if (found.isPresent() && found.get() < current) {
            this.logger.info("Updating the database schema from version {} to {}", found.get(),
                    current);
            this.updater.update(dsl, found.get(), current);
        }

        for (final SbTable<?> table : SakuraTables.all()) {
            createTable(dsl, table).execute();
        }
        for (final SbTable<?> table : SakuraTables.all()) {
            this.createMissingIndexes(dsl, table, schema);
        }
        dsl.insertInto(SakuraTables.SCOPES)
                .set(SakuraTables.SCOPES.KIND, ScopeKind.GLOBAL)
                .set(SakuraTables.SCOPES.VALUE, "*")
                .onConflictDoNothing()
                .execute();
        if (found.isEmpty()) {
            dsl.insertInto(SakuraTables.META)
                    .set(SakuraTables.META.K, CREATED_WITH_KEY)
                    .set(SakuraTables.META.V, BuildConstants.VERSION)
                    .onConflictDoNothing()
                    .execute();
            this.logger.info("Created the database schema (version {})", current);
        }
        dsl.insertInto(SakuraTables.META)
                .set(SakuraTables.META.K, VERSION_KEY)
                .set(SakuraTables.META.V, String.valueOf(current))
                .onDuplicateKeyUpdate()
                .set(SakuraTables.META.V, String.valueOf(current))
                .execute();
    }

    Optional<Integer> version(final DSLContext dsl) {
        final String text = dsl.select(SakuraTables.META.V).from(SakuraTables.META)
                .where(SakuraTables.META.K.eq(VERSION_KEY))
                .fetchOne(SakuraTables.META.V);
        if (text == null) {
            return Optional.empty();
        }
        try {
            return Optional.of(Integer.parseInt(text.strip()));
        } catch (final NumberFormatException e) {
            throw new StorageException("The database schema version is not a number: " + text, e);
        }
    }

    public static Query createTable(final DSLContext dsl, final SbTable<?> table) {
        final List<Constraint> constraints = new ArrayList<>();
        final UniqueKey<?> primaryKey = table.getPrimaryKey();
        if (primaryKey != null) {
            constraints.add(DSL.constraint(DSL.name(primaryKey.getName()))
                    .primaryKey(primaryKey.getFieldsArray()));
        }
        for (final UniqueKey<?> unique : table.getUniqueKeys()) {
            constraints.add(DSL.constraint(DSL.name(unique.getName()))
                    .unique(unique.getFieldsArray()));
        }
        for (final ForeignKey<?, ?> reference : table.getReferences()) {
            final ConstraintForeignKeyOnStep step = DSL.constraint(DSL.name(reference.getName()))
                    .foreignKey(reference.getFieldsArray())
                    .references(reference.getKey().getTable(), reference.getKeyFieldsArray());
            constraints.add(switch (reference.getDeleteRule()) {
                case CASCADE -> step.onDeleteCascade();
                case SET_NULL -> step.onDeleteSetNull();
                case null, default -> step;
            });
        }
        constraints.addAll(table.checks());
        return dsl.createTableIfNotExists(table).columns(table.fields()).constraints(constraints);
    }

    public static Query createIndex(final DSLContext dsl, final Index index) {
        return dsl.createIndex(DSL.name(index.getName()))
                .on(index.getTable(), index.getFields());
    }

    private void createMissingIndexes(final DSLContext dsl, final SbTable<?> table,
            final @Nullable String schema) {
        final List<Index> indexes = table.getIndexes();
        if (indexes.isEmpty()) {
            return;
        }
        final Set<String> existing = existingIndexes(dsl, table, schema);
        for (final Index index : indexes) {
            if (existing.contains(index.getName().toLowerCase(Locale.ROOT))) {
                continue;
            }
            try {
                createIndex(dsl, index).execute();
            } catch (final DataAccessException e) {
                // Another instance may have created it in the meantime.
                if (!existingIndexes(dsl, table, schema)
                        .contains(index.getName().toLowerCase(Locale.ROOT))) {
                    throw e;
                }
            }
        }
    }

    private static Set<String> existingIndexes(final DSLContext dsl, final SbTable<?> table,
            final @Nullable String schema) {
        return dsl.connectionResult(connection -> {
            final DatabaseMetaData metaData = connection.getMetaData();
            final String schemaName = schema != null ? schema : connection.getSchema();
            final Set<String> names = new HashSet<>();
            try (ResultSet rows = metaData.getIndexInfo(
                    connection.getCatalog(), schemaName, table.getName(), false, true)) {
                while (rows.next()) {
                    final String name = rows.getString("INDEX_NAME");
                    if (name != null) {
                        names.add(name.toLowerCase(Locale.ROOT));
                    }
                }
            }
            return names;
        });
    }
}
