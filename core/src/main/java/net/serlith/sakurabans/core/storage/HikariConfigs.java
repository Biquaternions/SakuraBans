package net.serlith.sakurabans.core.storage;

import com.zaxxer.hikari.HikariConfig;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import net.serlith.sakurabans.core.configuration.settings.StorageSettings;
import net.serlith.sakurabans.core.configuration.settings.StorageType;
import org.jooq.SQLDialect;

final class HikariConfigs {

    private HikariConfigs() {
    }

    static HikariConfig create(final StorageSettings settings, final Path dataDirectory) {
        final HikariConfig config = new HikariConfig();
        config.setPoolName("SakuraBans");
        config.setTransactionIsolation("TRANSACTION_READ_COMMITTED");
        config.setDriverClassName(switch (settings.type()) {
            case H2 -> org.h2.Driver.class.getName();
            case MARIADB -> org.mariadb.jdbc.Driver.class.getName();
            case MYSQL -> com.mysql.cj.jdbc.Driver.class.getName();
            case POSTGRESQL -> org.postgresql.Driver.class.getName();
        });
        config.setJdbcUrl(switch (settings.type()) {
            case H2 -> "jdbc:h2:file:" + dataDirectory.resolve(settings.h2File()).toAbsolutePath();
            case MARIADB -> url("mariadb", settings);
            case MYSQL -> url("mysql", settings);
            case POSTGRESQL -> url("postgresql", settings);
        });
        if (settings.type() == StorageType.H2) {
            config.setUsername("sa");
            config.setPassword("");
        } else {
            config.setUsername(settings.username());
            config.setPassword(settings.password());
        }
        final Map<String, String> properties = new LinkedHashMap<>();
        if (settings.type() == StorageType.MYSQL) {
            properties.put("characterEncoding", "UTF-8");
            properties.put("cachePrepStmts", "true");
            properties.put("prepStmtCacheSize", "250");
            properties.put("prepStmtCacheSqlLimit", "2048");
            properties.put("useServerPrepStmts", "true");
        }
        // The owner's properties win over our defaults.
        properties.putAll(settings.properties());
        properties.forEach(config::addDataSourceProperty);

        final StorageSettings.Pool pool = settings.pool();
        config.setMaximumPoolSize(pool.maximumSize());
        config.setMinimumIdle(pool.minimumIdle());
        config.setConnectionTimeout(pool.connectionTimeout().toMillis());
        config.setMaxLifetime(pool.maxLifetime().toMillis());
        return config;
    }

    static SQLDialect dialect(final StorageSettings settings) {
        return switch (settings.type()) {
            case H2 -> SQLDialect.H2;
            case MARIADB -> SQLDialect.MARIADB;
            case MYSQL -> SQLDialect.MYSQL;
            case POSTGRESQL -> SQLDialect.POSTGRES;
        };
    }

    private static String url(final String scheme, final StorageSettings settings) {
        return "jdbc:" + scheme + "://" + settings.host() + ":" + settings.effectivePort() + "/"
                + settings.database();
    }
}
