package net.serlith.sakurabans.core;

import static org.assertj.core.api.Assertions.assertThat;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Driver;
import java.util.Map;
import org.jooq.SQLDialect;
import org.jooq.conf.ParamType;
import org.jooq.impl.DSL;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.spongepowered.configurate.CommentedConfigurationNode;
import org.spongepowered.configurate.objectmapping.ConfigSerializable;
import org.spongepowered.configurate.objectmapping.meta.Comment;
import org.spongepowered.configurate.yaml.NodeStyle;
import org.spongepowered.configurate.yaml.YamlConfigurationLoader;

/**
 * The library behaviour S03 proved on the platforms that a plain classpath can reproduce
 * (plan 3.6). Packaging itself (relocation, class loaders) is only visible on runServer and
 * runVelocity.
 */
class LibraryCompatibilityTest {

    private static final Map<Class<? extends Driver>, String> DRIVERS = Map.of(
            org.h2.Driver.class, "jdbc:h2:mem:",
            org.mariadb.jdbc.Driver.class, "jdbc:mariadb://localhost/sakurabans",
            com.mysql.cj.jdbc.Driver.class, "jdbc:mysql://localhost/sakurabans",
            org.postgresql.Driver.class, "jdbc:postgresql://localhost/sakurabans");

    @ParameterizedTest
    @EnumSource(value = SQLDialect.class, names = {"H2", "MARIADB", "MYSQL", "POSTGRES"})
    void jooqRendersEverySupportedDialect(final SQLDialect dialect) {
        final String sql = DSL.using(dialect)
                .select(DSL.field(DSL.name("id")))
                .from(DSL.table(DSL.name("sb_check")))
                .where(DSL.field(DSL.name("id")).eq(1))
                .getSQL(ParamType.INLINED);

        assertThat(sql).containsIgnoringCase("sb_check").endsWith("= 1");
    }

    @Test
    void everyDriverAcceptsItsUrlWithoutDriverManager() throws Exception {
        for (final Map.Entry<Class<? extends Driver>, String> entry : DRIVERS.entrySet()) {
            final Driver driver = entry.getKey().getDeclaredConstructor().newInstance();

            assertThat(driver.acceptsURL(entry.getValue())).as(entry.getValue()).isTrue();
        }
    }

    @Test
    void hikariRunsJooqOnH2() {
        final HikariConfig config = new HikariConfig();
        config.setDriverClassName(org.h2.Driver.class.getName());
        config.setJdbcUrl("jdbc:h2:mem:sakurabans_library_test");
        config.setMaximumPoolSize(1);
        try (HikariDataSource dataSource = new HikariDataSource(config)) {
            final Integer one = DSL.using(dataSource, SQLDialect.H2)
                    .selectOne()
                    .fetchOne(0, Integer.class);

            assertThat(one).isEqualTo(1);
        }
    }

    @Test
    void configurateRoundTripsWithoutComments(@TempDir final Path directory) throws Exception {
        final Path file = directory.resolve("check.yml");
        final YamlConfigurationLoader loader = YamlConfigurationLoader.builder()
                .path(file)
                .nodeStyle(NodeStyle.BLOCK)
                .build();
        final CommentedConfigurationNode node = loader.createNode();
        node.set(CheckConfig.class, new CheckConfig());

        loader.save(node);

        // Configurate 4.2.0 doesn't write @Comment in YAML (plan 10.3), which is why defaults
        // ship as commented resources. If this starts failing, comments are written now.
        assertThat(Files.readString(file)).contains("greeting: hello")
                .doesNotContain("This comment is not written.");
        assertThat(loader.load().get(CheckConfig.class).greeting).isEqualTo("hello");
    }

    @ConfigSerializable
    static final class CheckConfig {

        @Comment("This comment is not written.")
        private String greeting = "hello";
    }
}
