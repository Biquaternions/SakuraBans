package net.serlith.sakurabans.core.configuration.settings;

import java.time.Duration;
import java.util.Map;
import java.util.Optional;

public record StorageSettings(
        StorageType type,
        String h2File,
        String host,
        int port,
        String database,
        String username,
        String password,
        String tablePrefix,
        Optional<String> schema,
        Map<String, String> properties,
        Pool pool,
        Sync sync) {

    public StorageSettings {
        properties = Map.copyOf(properties);
    }

    public int effectivePort() {
        return this.port == 0 ? this.type.defaultPort() : this.port;
    }

    public record Pool(int maximumSize, int minimumIdle, Duration connectionTimeout,
            Duration maxLifetime) {
    }

    public record Sync(SyncMode mode, Duration pollInterval) {
    }

    // The password must never reach a log.
    @Override
    public String toString() {
        return "StorageSettings[type=" + this.type + ", host=" + this.host + ", port="
                + this.effectivePort() + ", database=" + this.database + ", username="
                + this.username + ", password=***, tablePrefix=" + this.tablePrefix + "]";
    }
}
