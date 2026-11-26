package net.serlith.sakurabans.core.importer;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.UUID;
import net.serlith.sakurabans.core.ServiceTest;
import org.jooq.DSLContext;
import org.jooq.SQLDialect;
import org.jooq.impl.DSL;
import org.junit.jupiter.api.AfterEach;

/**
 * Base of importer tests (S38-S41): the services of {@link ServiceTest}, in-memory source
 * databases filled from SQL fixtures, and imports run the way {@code /sb import} runs them.
 *
 * <pre>{@code
 * class LiteBansSourceTest extends ImportTest {
 *     @Override
 *     protected ImportSources sources() {
 *         return new ImportSources(List.of(new LiteBansSource()));
 *     }
 *
 *     @Test
 *     void importsEverythingOnce() {
 *         this.fixture(Engine.H2, "litebans", "litebans_").script("litebans/fixture.sql");
 *         final ImportReport first = this.importFrom("litebans");
 *         Assertions.assertThat(first.imported()).isEqualTo(4);
 *         Assertions.assertThat(this.importFrom("litebans").imported()).isZero();
 *     }
 * }
 * }</pre>
 */
public abstract class ImportTest extends ServiceTest {

    public enum Engine { H2, HSQLDB }

    private final Map<String, String> sections = new LinkedHashMap<>();
    private final List<Fixture> fixtures = new ArrayList<>();
    private ImportJobs jobs;

    /** The sources under test; the shipped ones by default. */
    protected ImportSources sources() {
        return ImportSources.standard();
    }

    @AfterEach
    final void closeFixtures() {
        this.fixtures.forEach(Fixture::close);
    }

    /** The import framework, wired like {@code CoreComponents} with {@link #sources()}. */
    protected final ImportJobs jobs() {
        if (this.jobs == null) {
            this.jobs = this.importJobs(this.sources());
        }
        return this.jobs;
    }

    /**
     * A new, empty in-memory database, and the {@code import.yml} section that names it with
     * the given table prefix. It lives until the test ends.
     */
    protected final Fixture fixture(final Engine engine, final String section,
            final String tablePrefix) {
        return this.fixture(engine, section, tablePrefix, "");
    }

    /**
     * Like {@link #fixture(Engine, String, String)}, with options appended to the JDBC URL, for
     * example {@code ;MODE=MySQL} for H2.
     */
    protected final Fixture fixture(final Engine engine, final String section,
            final String tablePrefix, final String urlOptions) {
        final String name = "fixture" + UUID.randomUUID().toString().replace("-", "");
        final String url = (engine == Engine.H2 ? "jdbc:h2:mem:" : "jdbc:hsqldb:mem:") + name
                + urlOptions;
        final String user = engine == Engine.H2 ? "sa" : "SA";
        final Fixture fixture = new Fixture(url, user, engine == Engine.H2
                ? SQLDialect.H2 : SQLDialect.HSQLDB);
        this.fixtures.add(fixture);
        this.section(section, "  jdbc-url: '" + url + "'\n  username: '" + user + "'\n"
                + "  password: ''\n  table-prefix: '" + tablePrefix + "'\n");
        return fixture;
    }

    /** Points the {@code vanilla} section of {@code import.yml} at these files. */
    protected final void vanilla(final Path bannedPlayers, final Path bannedIps) {
        this.section("vanilla", "  banned-players: '" + bannedPlayers.toAbsolutePath() + "'\n"
                + "  banned-ips: '" + bannedIps.toAbsolutePath() + "'\n");
    }

    /** Writes any section of {@code import.yml}; {@code body} is its indented YAML. */
    protected final void section(final String section, final String body) {
        this.sections.put(section, body);
        final StringBuilder yaml = new StringBuilder();
        this.sections.forEach((name, text) -> yaml.append(name).append(":\n").append(text));
        try {
            Files.writeString(this.dir.resolve("import.yml"), yaml.toString());
        } catch (final IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** Imports from a source like {@code /sb import <id> --confirm}; returns the report. */
    protected final ImportReport importFrom(final String id) {
        return this.importFrom(id, false);
    }

    /** {@link #importFrom(String)}, or a dry run. Fails the test unless the import finished. */
    protected final ImportReport importFrom(final String id, final boolean dryRun) {
        final ImportOutcome outcome = this.outcome(id, dryRun);
        if (outcome instanceof ImportOutcome.Done done) {
            return done.report();
        }
        throw new AssertionError("the import from " + id + " didn't finish: " + outcome);
    }

    /** Whatever an import from the source ends with (done, refused, busy). */
    protected final ImportOutcome outcome(final String id, final boolean dryRun) {
        return this.jobs().run(id, dryRun, CONSOLE).join();
    }

    /** The text of a file under {@code core/src/test/resources/import/}. */
    protected static String resource(final String path) {
        try (InputStream input = ImportTest.class.getResourceAsStream("/import/" + path)) {
            if (input == null) {
                throw new IllegalArgumentException("no test resource import/" + path);
            }
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        } catch (final IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** An in-memory source database, kept open by one connection of its own. */
    public static final class Fixture implements AutoCloseable {

        private final String url;
        private final Connection keeper;
        private final DSLContext dsl;

        Fixture(final String url, final String user, final SQLDialect dialect) {
            this.url = url;
            final Properties properties = new Properties();
            properties.setProperty("user", user);
            properties.setProperty("password", "");
            try {
                this.keeper = dialect == SQLDialect.H2
                        ? new org.h2.Driver().connect(url, properties)
                        : new org.hsqldb.jdbc.JDBCDriver().connect(url, properties);
            } catch (final SQLException e) {
                throw new IllegalStateException(e);
            }
            this.dsl = DSL.using(this.keeper, dialect);
        }

        public String jdbcUrl() {
            return this.url;
        }

        /** For setting up rows in Java; it writes with auto-commit. */
        public DSLContext dsl() {
            return this.dsl;
        }

        /**
         * Runs an SQL script under {@code core/src/test/resources/import/}, for example
         * {@code litebans/fixture.sql}. Statements end with {@code ;} at the end of a line;
         * lines starting with {@code --} are comments.
         */
        public Fixture script(final String path) {
            final StringBuilder statement = new StringBuilder();
            for (final String line : resource(path).split("\\R")) {
                if (line.strip().startsWith("--")) {
                    continue;
                }
                statement.append(line).append('\n');
                if (line.strip().endsWith(";")) {
                    final String sql = statement.toString().strip();
                    this.execute(sql.substring(0, sql.length() - 1));
                    statement.setLength(0);
                }
            }
            if (!statement.toString().isBlank()) {
                this.execute(statement.toString());
            }
            return this;
        }

        /** Runs one SQL statement. */
        public Fixture execute(final String sql) {
            try (Statement statement = this.keeper.createStatement()) {
                statement.execute(sql);
            } catch (final SQLException e) {
                throw new IllegalStateException("fixture statement failed: " + sql, e);
            }
            return this;
        }

        @Override
        public void close() {
            try (Statement statement = this.keeper.createStatement()) {
                statement.execute("SHUTDOWN");
            } catch (final SQLException e) {
                // Already gone.
            }
            try {
                this.keeper.close();
            } catch (final SQLException e) {
                // Already gone.
            }
        }
    }
}
