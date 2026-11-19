package net.serlith.sakurabans.core.bootstrap;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import de.bsommerfeld.jshepherd.annotation.Comment;
import de.bsommerfeld.jshepherd.annotation.Key;
import de.bsommerfeld.jshepherd.core.ConfigurablePojo;
import de.bsommerfeld.jshepherd.core.ConfigurationLoader;
import de.bsommerfeld.jshepherd.core.PersistenceDelegateFactory;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.CodeSource;
import java.sql.Driver;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.logger.slf4j.ComponentLogger;
import org.incendo.cloud.CommandManager;
import org.incendo.cloud.minecraft.extras.MinecraftExceptionHandler;
import org.incendo.cloud.minecraft.extras.MinecraftHelp;
import org.jooq.DSLContext;
import org.jooq.SQLDialect;
import org.jooq.conf.ParamType;
import org.jooq.impl.DSL;
import org.yaml.snakeyaml.Yaml;

public final class PackagingChecks {

    /** The system property that turns the checks on. */
    public static final String PROPERTY = "sakurabans.selfCheck";

    /** The command the Cloud check registers. */
    public static final String COMMAND = "sakurabans-check";

    private static final Map<Class<? extends Driver>, String> DRIVERS = Map.of(
            org.h2.Driver.class, "jdbc:h2:mem:",
            org.mariadb.jdbc.Driver.class, "jdbc:mariadb://localhost/sakurabans",
            com.mysql.cj.jdbc.Driver.class, "jdbc:mysql://localhost/sakurabans",
            org.postgresql.Driver.class, "jdbc:postgresql://localhost/sakurabans");

    private PackagingChecks() {
    }

    /**
     * Whether the checks are turned on.
     *
     * @return {@code true} if {@value #PROPERTY} is {@code true}
     */
    public static boolean enabled() {
        return Boolean.getBoolean(PROPERTY);
    }

    /**
     * Checks the libraries that don't need the platform: jOOQ, Hikari, the JDBC drivers and
     * JShepherd. Logs one line per check and a summary.
     *
     * @param logger the plugin logger
     * @param dataDirectory the plugin data directory, for a temporary YAML file
     * @return {@code true} if every check passed
     */
    public static boolean checkLibraries(final ComponentLogger logger, final Path dataDirectory) {
        final List<String> failures = new ArrayList<>();
        logger.info("[self-check] Adventure from {}", origin(Component.class));
        check(logger, failures, "jOOQ rendering", () -> renderQueries(logger));
        check(logger, failures, "JDBC drivers", () -> lookUpDrivers(logger));
        check(logger, failures, "Hikari + jOOQ on H2", PackagingChecks::queryH2);
        check(logger, failures, "JShepherd YAML", () -> loadYaml(logger, dataDirectory));
        if (failures.isEmpty()) {
            logger.info("[self-check] libraries OK");
        } else {
            logger.error("[self-check] libraries FAILED: {}", failures);
        }
        return failures.isEmpty();
    }

    /**
     * Registers {@value #COMMAND}, which renders a help page through {@code MinecraftHelp}, and
     * installs the default {@code MinecraftExceptionHandler}s. Run the command from the console to
     * check that Cloud executes commands.
     *
     * @param logger the plugin logger
     * @param manager the platform's command manager
     * @param <C> the sender type
     */
    public static <C> void registerCloudCheck(
            final ComponentLogger logger, final CommandManager<C> manager) {
        MinecraftExceptionHandler.<C>create(sender -> Audience.empty())
                .defaultHandlers()
                .registerTo(manager);
        final MinecraftHelp<C> help =
                MinecraftHelp.create("/" + COMMAND, manager, sender -> Audience.empty());
        manager.command(manager.commandBuilder(COMMAND).handler(context -> {
            help.queryCommands("", context.sender());
            logger.info("[self-check] Cloud command executed, help rendered");
        }));
        logger.info("[self-check] Cloud manager {} OK; run '{}' to execute",
                manager.getClass().getSimpleName(), COMMAND);
    }

    private static void renderQueries(final ComponentLogger logger) {
        for (final SQLDialect dialect : List.of(
                SQLDialect.H2, SQLDialect.MARIADB, SQLDialect.MYSQL, SQLDialect.POSTGRES)) {
            final String sql = DSL.using(dialect)
                    .select(DSL.field(DSL.name("id")))
                    .from(DSL.table(DSL.name("sb_check")))
                    .where(DSL.field(DSL.name("id")).eq(1))
                    .getSQL(ParamType.INLINED);
            logger.info("[self-check]   {}: {}", dialect, sql);
        }
    }

    private static void lookUpDrivers(final ComponentLogger logger)
            throws ReflectiveOperationException, SQLException {
        // DriverManager can't see drivers loaded by a plugin class loader, so we never use it
        // (plan 3.6). The drivers are created directly, as Hikari does with driverClassName.
        for (final Map.Entry<Class<? extends Driver>, String> entry : DRIVERS.entrySet()) {
            final Driver driver = entry.getKey().getDeclaredConstructor().newInstance();
            if (!driver.acceptsURL(entry.getValue())) {
                throw new IllegalStateException(
                        driver.getClass().getName() + " rejects " + entry.getValue());
            }
            logger.info("[self-check]   {} {}.{}", driver.getClass().getName(),
                    driver.getMajorVersion(), driver.getMinorVersion());
        }
    }

    private static void queryH2() {
        final HikariConfig config = new HikariConfig();
        config.setDriverClassName(org.h2.Driver.class.getName());
        config.setJdbcUrl("jdbc:h2:mem:sakurabans_check");
        config.setMaximumPoolSize(1);
        try (HikariDataSource dataSource = new HikariDataSource(config)) {
            final DSLContext dsl = DSL.using(dataSource, SQLDialect.H2);
            final Integer one = dsl.selectOne().fetchOne(0, Integer.class);
            if (one == null || one != 1) {
                throw new IllegalStateException("SELECT 1 returned " + one);
            }
        }
    }

    private static void loadYaml(final ComponentLogger logger, final Path dataDirectory)
            throws IOException {
        logger.info("[self-check]   SnakeYAML from {}", origin(Yaml.class));
        final Thread thread = Thread.currentThread();
        final ClassLoader previous = thread.getContextClassLoader();
        final ClassLoader libraries = PersistenceDelegateFactory.class.getClassLoader();
        logger.info("[self-check]   context class loader is the library loader: {}",
                previous == libraries);
        Files.createDirectories(dataDirectory);
        final Path file = dataDirectory.resolve("packaging-check.yml");
        thread.setContextClassLoader(libraries);
        try {
            final CheckConfig config = ConfigurationLoader.load(file, CheckConfig::new);
            if (!"hello".equals(config.greeting)) {
                throw new IllegalStateException("greeting was " + config.greeting);
            }
            if (!Files.readString(file).contains("must survive")) {
                throw new IllegalStateException("the comment was not written");
            }
        } finally {
            thread.setContextClassLoader(previous);
            Files.deleteIfExists(file);
        }
    }

    private static void check(final ComponentLogger logger, final List<String> failures,
            final String name, final Check check) {
        try {
            check.run();
            logger.info("[self-check] {} OK", name);
        } catch (final Exception | LinkageError e) {
            failures.add(name);
            logger.error("[self-check] {} FAILED", name, e);
        }
    }

    private static String origin(final Class<?> type) {
        final CodeSource source = type.getProtectionDomain().getCodeSource();
        final String location = source == null ? "the platform" : source.getLocation().toString();
        return type.getName() + " (" + location + ")";
    }

    @FunctionalInterface
    private interface Check {
        void run() throws Exception;
    }

    /** A tiny JShepherd model with one commented key. */
    static final class CheckConfig extends ConfigurablePojo<CheckConfig> {

        @Key("greeting")
        @Comment("This comment must survive.")
        private String greeting = "hello";
    }
}
