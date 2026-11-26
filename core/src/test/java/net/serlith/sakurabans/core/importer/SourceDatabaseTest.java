package net.serlith.sakurabans.core.importer;

import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.serlith.sakurabans.api.model.GlobalScope;
import net.serlith.sakurabans.api.model.PunishmentType;
import net.serlith.sakurabans.api.model.Victim;
import net.serlith.sakurabans.api.model.Visibility;
import net.serlith.sakurabans.core.configuration.settings.ImportSettings;
import net.serlith.sakurabans.core.storage.dbo.SakuraTables;
import org.assertj.core.api.Assertions;
import org.jooq.Cursor;
import org.jooq.Field;
import org.jooq.Record;
import org.jooq.exception.DataAccessException;
import org.junit.jupiter.api.Test;

/** A JDBC source end to end, on HSQLDB, the way S39-S41 sources work. */
class SourceDatabaseTest extends ImportTest {

    /** Reads the made-up plugin of {@code import/example/fixture.sql}. */
    static final class ExampleSource implements ImportSource {

        private static final Field<Integer> ID = SourceDatabase.field("id", Integer.class);
        private static final Field<String> UUID_COLUMN =
                SourceDatabase.field("uuid", String.class);
        private static final Field<String> IP = SourceDatabase.field("ip", String.class);
        private static final Field<String> REASON = SourceDatabase.field("reason", String.class);
        private static final Field<String> BANNED_BY =
                SourceDatabase.field("banned_by", String.class);
        private static final Field<Long> CREATED = SourceDatabase.field("created", Long.class);
        private static final Field<Long> UNTIL = SourceDatabase.field("ends", Long.class);

        @Override
        public String id() {
            return "example";
        }

        @Override
        public Optional<String> databaseSection() {
            return Optional.of("libertybans");
        }

        @Override
        public Map<String, Long> survey(final SourceContext context) {
            return Map.of("bans", context.database().count("bans"));
        }

        @Override
        public void read(final SourceContext context, final SourceReader reader) {
            final SourceDatabase database = context.database();
            try (Cursor<? extends Record> rows = database.stream(database.dsl()
                    .select(ID, UUID_COLUMN, IP, REASON, BANNED_BY, CREATED, UNTIL)
                    .from(database.table("bans")).orderBy(ID))) {
                for (final Record row : rows) {
                    final String id = row.get(ID).toString();
                    final UUID uuid = ImportValues.uuid(row.get(UUID_COLUMN)).orElse(null);
                    final Optional<Victim> victim = ImportValues.victim(uuid,
                            ImportValues.address(row.get(IP)).orElse(null));
                    if (victim.isEmpty()) {
                        reader.failure("bans", id, "neither a UUID nor an address");
                        continue;
                    }
                    reader.punishment(new ImportedPunishment(PunishmentType.BAN,
                            victim.get(), ImportValues.operator(null, row.get(BANNED_BY)),
                            ImportValues.seconds(row.get(CREATED)).orElseThrow(),
                            ImportValues.seconds(row.get(UNTIL)).orElse(null),
                            GlobalScope.INSTANCE, reader.reason(row.get(REASON)),
                            Visibility.NORMAL, false, null, null,
                            context.externalId("bans", id)));
                }
            }
        }
    }

    @Override
    protected ImportSources sources() {
        return new ImportSources(List.of(new ExampleSource()));
    }

    @Test
    void jdbcSourcesStreamTheirTablesOnceAndRepeatsSkipThem() {
        this.fixture(Engine.HSQLDB, "libertybans", "ex_").script("example/fixture.sql");

        Assertions.assertThat(this.jobs().survey("example").join())
                .isEqualTo(new ImportOutcome.Surveyed(Map.of("bans", 3L)));
        final ImportReport first = this.importFrom("example");

        Assertions.assertThat(first.imported()).isEqualTo(2);
        Assertions.assertThat(first.failed()).isEqualTo(1);
        Assertions.assertThat(first.failures().getFirst().externalId())
                .contains("example:bans:3");
        Assertions.assertThat(this.count(SakuraTables.PUNISHMENTS,
                SakuraTables.PUNISHMENTS.EXTERNAL_ID.eq("example:bans:1"))).isEqualTo(1);
        final String reason = this.read(dsl -> dsl.select(SakuraTables.PUNISHMENTS.REASON)
                .from(SakuraTables.PUNISHMENTS)
                .where(SakuraTables.PUNISHMENTS.EXTERNAL_ID.eq("example:bans:1"))
                .fetchSingle().value1());
        Assertions.assertThat(reason).isEqualTo("Cheating");

        final ImportReport second = this.importFrom("example");
        Assertions.assertThat(second.imported()).isZero();
        Assertions.assertThat(second.skipped()).isEqualTo(2);
    }

    @Test
    void missingSectionUrlsOrTablesAreReported() {
        this.section("libertybans", "  jdbc-url: ''\n");
        Assertions.assertThat(this.outcome("example", false)).isEqualTo(
                new ImportOutcome.Refused(ImportOutcome.Problem.NOT_CONFIGURED, "libertybans"));

        this.fixture(Engine.HSQLDB, "libertybans", "ex_");
        Assertions.assertThat(this.jobs().survey("example").join())
                .isInstanceOfSatisfying(ImportOutcome.Refused.class, refused ->
                        Assertions.assertThat(refused.problem())
                                .isEqualTo(ImportOutcome.Problem.UNREADABLE));
        Assertions.assertThat(this.outcome("example", false))
                .isInstanceOfSatisfying(ImportOutcome.Refused.class, refused ->
                        Assertions.assertThat(refused.problem())
                                .isEqualTo(ImportOutcome.Problem.FAILED));
    }

    @Test
    void sourcesAreOpenedReadOnlyByTheirUrl() throws Exception {
        final Fixture fixture = this.fixture(Engine.HSQLDB, "libertybans", "ex_")
                .script("example/fixture.sql");
        final ImportSettings.Database settings = new ImportSettings.Database("libertybans",
                fixture.jdbcUrl(), "SA", "secret", "ex_");

        Assertions.assertThat(settings.toString()).doesNotContain("secret");
        Assertions.assertThat(SourceDatabase.dialectOf("jdbc:hsqldb:mem:x")).isPresent();
        Assertions.assertThat(SourceDatabase.dialectOf("jdbc:sqlite:x")).isEmpty();
        try (SourceDatabase database = SourceDatabase.open(new ImportSettings.Database(
                "libertybans", fixture.jdbcUrl(), "SA", "", "ex_"))) {
            Assertions.assertThat(database.exists("bans")).isTrue();
            Assertions.assertThat(database.exists("kicks")).isFalse();
            Assertions.assertThat(database.count("bans")).isEqualTo(3);
            Assertions.assertThatThrownBy(() -> database.dsl()
                            .deleteFrom(database.table("bans")).execute())
                    .isInstanceOf(DataAccessException.class);
        }
        Assertions.assertThatThrownBy(() -> SourceDatabase.open(new ImportSettings.Database(
                        "litebans", "jdbc:sqlite:file.db", "", "", "")))
                .isInstanceOf(SQLException.class);
        Assertions.assertThatThrownBy(() -> SourceDatabase.open(new ImportSettings.Database(
                        "litebans", "jdbc:h2:" + this.dir.resolve("missing"), "", "", "")))
                .isInstanceOf(SQLException.class);
    }
}
