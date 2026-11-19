package net.serlith.sakurabans.core.storage;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import net.serlith.sakurabans.core.storage.dbo.SakuraTables;
import net.serlith.sakurabans.core.storage.dbo.SbTable;
import org.jooq.DSLContext;
import org.jooq.Index;
import org.jooq.SQLDialect;
import org.jooq.conf.ParamType;
import org.jooq.impl.DSL;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

/**
 * The DDL of every supported dialect, compared with {@code src/test/resources/schema}. Set
 * {@code UPDATE_SNAPSHOTS=1} to rewrite the files after an intended schema change, then review
 * the diff.
 */
class SchemaSnapshotTest {

    private static final Path SNAPSHOTS = Path.of("src/test/resources/schema");

    @ParameterizedTest
    @EnumSource(value = SQLDialect.class, names = {"H2", "MARIADB", "MYSQL", "POSTGRES"})
    void theDdlMatchesTheSnapshot(final SQLDialect dialect) throws Exception {
        SakuraTables.initialize(H2Database.PREFIX, Optional.empty());
        final DSLContext dsl =
                DSL.using(dialect, StorageService.jooqSettings(Optional.empty()));
        final List<String> statements = new ArrayList<>();
        for (final SbTable<?> table : SakuraTables.all()) {
            statements.add(SchemaManager.createTable(dsl, table).getSQL(ParamType.INLINED));
        }
        for (final SbTable<?> table : SakuraTables.all()) {
            for (final Index index : table.getIndexes()) {
                statements.add(SchemaManager.createIndex(dsl, index).getSQL(ParamType.INLINED));
            }
        }
        final String rendered = String.join(";\n", statements) + ";\n";

        final Path file = SNAPSHOTS.resolve(dialect.name().toLowerCase(Locale.ROOT) + ".sql");
        if ("1".equals(System.getenv("UPDATE_SNAPSHOTS")) || Files.notExists(file)) {
            Files.createDirectories(SNAPSHOTS);
            Files.writeString(file, rendered);
        }
        assertThat(rendered).isEqualTo(Files.readString(file));
    }
}
