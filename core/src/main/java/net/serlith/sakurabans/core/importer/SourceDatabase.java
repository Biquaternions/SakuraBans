package net.serlith.sakurabans.core.importer;

import com.google.common.base.Preconditions;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.Driver;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Properties;
import net.serlith.sakurabans.core.configuration.settings.ImportSettings;
import org.jooq.Cursor;
import org.jooq.DSLContext;
import org.jooq.DataType;
import org.jooq.Field;
import org.jooq.Record;
import org.jooq.ResultQuery;
import org.jooq.SQLDialect;
import org.jooq.Table;
import org.jooq.conf.Settings;
import org.jooq.impl.DSL;

/**
 * The database an {@link ImportSource} reads, opened read-only (where the driver supports it)
 * with one plain JDBC connection from its {@code import.yml} section. The driver is picked from
 * the URL; {@code DriverManager} is never used.
 *
 * <p>{@link #table} and {@link #field} name things unquoted, as other plugins create them, so
 * the database folds the case as it did then (H2 and HSQLDB: upper case). Write
 * {@code DSL.quotedName} for an exact name.
 */
public final class SourceDatabase implements AutoCloseable {

    /** Rows fetched per round trip when streaming. */
    public static final int FETCH_SIZE = 500;

    private static final List<Vendor> VENDORS = List.of(
            new Vendor("jdbc:mariadb:", SQLDialect.MARIADB, org.mariadb.jdbc.Driver::new),
            new Vendor("jdbc:mysql:", SQLDialect.MYSQL, com.mysql.cj.jdbc.Driver::new),
            new Vendor("jdbc:postgresql:", SQLDialect.POSTGRES, org.postgresql.Driver::new),
            new Vendor("jdbc:h2:", SQLDialect.H2, org.h2.Driver::new),
            new Vendor("jdbc:hsqldb:", SQLDialect.HSQLDB, org.hsqldb.jdbc.JDBCDriver::new));

    private final Connection connection;
    private final DSLContext dsl;
    private final String prefix;

    private SourceDatabase(final Connection connection, final SQLDialect dialect,
            final String prefix) {
        this.connection = connection;
        this.dsl = DSL.using(connection, dialect, new Settings()
                .withRenderCatalog(false)
                .withExecuteLogging(false));
        this.prefix = prefix;
    }

    @FunctionalInterface
    private interface DriverFactory {
        Driver create() throws SQLException;
    }

    private record Vendor(String scheme, SQLDialect dialect, DriverFactory driver) {
    }

    public static Optional<SQLDialect> dialectOf(final String jdbcUrl) {
        return vendor(jdbcUrl).map(Vendor::dialect);
    }

    private static Optional<Vendor> vendor(final String jdbcUrl) {
        return VENDORS.stream().filter(vendor -> jdbcUrl.startsWith(vendor.scheme()))
                .findFirst();
    }

    public static SourceDatabase open(final ImportSettings.Database settings)
            throws SQLException {
        final String url = settings.jdbcUrl();
        Preconditions.checkArgument(!url.isBlank(), "%s has no jdbc-url", settings.section());
        final Vendor vendor = vendor(url).orElseThrow(() -> new SQLException(
                "no supported driver for " + url));
        final Properties properties = new Properties();
        if (!settings.username().isEmpty()) {
            properties.setProperty("user", settings.username());
        }
        properties.setProperty("password", settings.password());
        if (!url.contains(":mem:")) {
            // Never create an empty database because of a typo in the path.
            if (vendor.dialect() == SQLDialect.H2) {
                properties.setProperty("IFEXISTS", "TRUE");
            } else if (vendor.dialect() == SQLDialect.HSQLDB) {
                properties.setProperty("ifexists", "true");
                // Releases the files when the import closes the connection.
                properties.setProperty("shutdown", "true");
            }
        }
        final Connection connection = vendor.driver().create().connect(url, properties);
        if (connection == null) {
            throw new SQLException("the driver doesn't take " + url);
        }
        try {
            try {
                connection.setReadOnly(true);
            } catch (final SQLException e) {
                // A hint only; some drivers refuse it.
            }
            // PostgreSQL only streams (fetch size) inside a transaction.
            connection.setAutoCommit(false);
        } catch (final SQLException | RuntimeException e) {
            connection.close();
            throw e;
        }
        return new SourceDatabase(connection, vendor.dialect(), settings.tablePrefix());
    }

    public DSLContext dsl() {
        return this.dsl;
    }

    public SQLDialect dialect() {
        return this.dsl.dialect();
    }

    /** The {@code table-prefix} of the section. */
    public String prefix() {
        return this.prefix;
    }

    /** A table by its name without prefix, unquoted. */
    public Table<Record> table(final String name) {
        return DSL.table(DSL.unquotedName(this.prefix + name));
    }

    /** A column by its name, unquoted. */
    public static Field<Object> field(final String name) {
        return DSL.field(DSL.unquotedName(name));
    }

    /** A column by its name, unquoted, read as {@code type}. */
    public static <T> Field<T> field(final String name, final Class<T> type) {
        return DSL.field(DSL.unquotedName(name), type);
    }

    /** A column by its name, unquoted, read as {@code type}. */
    public static <T> Field<T> field(final String name, final DataType<T> type) {
        return DSL.field(DSL.unquotedName(name), type);
    }

    /** Whether a table (name without prefix) exists, in any case. */
    public boolean exists(final String name) {
        final String wanted = (this.prefix + name).toLowerCase(Locale.ROOT);
        return this.dsl.connectionResult(connection -> {
            final DatabaseMetaData metaData = connection.getMetaData();
            try (ResultSet tables = metaData.getTables(connection.getCatalog(), null, null,
                    new String[] {"TABLE"})) {
                while (tables.next()) {
                    final String found = tables.getString("TABLE_NAME");
                    if (found != null && found.toLowerCase(Locale.ROOT).equals(wanted)) {
                        return true;
                    }
                }
            }
            return false;
        });
    }

    /** The rows of a table (name without prefix). */
    public long count(final String name) {
        return this.dsl.fetchCount(this.table(name));
    }

    /**
     * Runs a query as a stream: {@link #FETCH_SIZE} rows at a time where the driver allows it,
     * never the whole result in memory. Close the cursor (try-with-resources). On MySQL no
     * other query may run on this database while a stream is open: read one table at a time,
     * or look up what a row refers to before or after the stream.
     */
    public <R extends Record> Cursor<R> stream(final ResultQuery<R> query) {
        final int fetchSize = this.dialect().family() == SQLDialect.MYSQL
                // Connector/J streams only with this value (or useCursorFetch in the URL).
                ? Integer.MIN_VALUE
                : FETCH_SIZE;
        return query.fetchSize(fetchSize).fetchLazy();
    }

    @Override
    public void close() {
        try {
            try {
                this.connection.rollback();
            } finally {
                this.connection.close();
            }
        } catch (final SQLException e) {
            // Nothing was written; the import is over either way.
        }
    }
}
