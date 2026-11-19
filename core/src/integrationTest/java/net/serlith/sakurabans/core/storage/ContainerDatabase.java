package net.serlith.sakurabans.core.storage;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;
import org.jspecify.annotations.Nullable;
import org.testcontainers.containers.JdbcDatabaseContainer;
import org.testcontainers.mariadb.MariaDBContainer;
import org.testcontainers.mysql.MySQLContainer;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * One container per database, started on first use and shared by every test in the JVM
 * (Testcontainers' Ryuk removes it afterwards). Each test still gets its own empty database.
 */
public enum ContainerDatabase implements TestDatabase {

    MARIADB("MARIADB", () -> new MariaDBContainer("mariadb:11.4")
            .withUsername("root").withPassword(ContainerDatabase.PASSWORD)),
    MYSQL("MYSQL", () -> new MySQLContainer("mysql:8.4")
            .withUsername("root").withPassword(ContainerDatabase.PASSWORD)),
    POSTGRESQL("POSTGRESQL", () -> new PostgreSQLContainer("postgres:17-alpine")
            .withPassword(ContainerDatabase.PASSWORD));

    private static final String PASSWORD = "sakura";
    private static final AtomicInteger NEXT = new AtomicInteger();

    private final String type;
    private final Supplier<JdbcDatabaseContainer<?>> factory;
    private @Nullable JdbcDatabaseContainer<?> container;

    ContainerDatabase(final String type, final Supplier<JdbcDatabaseContainer<?>> factory) {
        this.type = type;
        this.factory = factory;
    }

    public synchronized JdbcDatabaseContainer<?> container() {
        if (this.container == null) {
            final JdbcDatabaseContainer<?> started = this.factory.get();
            started.start();
            this.container = started;
        }
        return this.container;
    }

    @Override
    public Optional<String> freshStorageYaml() {
        final JdbcDatabaseContainer<?> container = this.container();
        final String name = "t" + NEXT.incrementAndGet();
        // A new database per test. Not a PostgreSQL schema: SakuraTables keeps the schema of the
        // first start for the whole JVM, and MariaDB and MySQL run in the same JVM without one.
        this.execute("CREATE DATABASE " + name);
        final String yaml =
                """
                type: %s
                host: %s
                port: %d
                database: %s
                username: %s
                password: %s
                table-prefix: sb_
                """;
        return Optional.of(yaml.formatted(this.type, container.getHost(),
                container.getMappedPort(this.port()), name, container.getUsername(),
                container.getPassword()));
    }

    private int port() {
        return this == POSTGRESQL ? 5432 : 3306;
    }

    private void execute(final String sql) {
        final JdbcDatabaseContainer<?> container = this.container();
        try (Connection connection = DriverManager.getConnection(container.getJdbcUrl(),
                container.getUsername(), container.getPassword());
                Statement statement = connection.createStatement()) {
            statement.execute(sql);
        } catch (final SQLException e) {
            throw new IllegalStateException(e);
        }
    }
}
