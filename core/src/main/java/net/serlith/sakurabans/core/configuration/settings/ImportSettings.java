package net.serlith.sakurabans.core.configuration.settings;

import java.nio.file.Path;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

public record ImportSettings(Map<String, Database> databases, Vanilla vanilla) {

    public static final String SELF = "self";
    public static final String LIBERTYBANS = "libertybans";
    public static final String LITEBANS = "litebans";
    public static final String ADVANCEDBAN = "advancedban";
    public static final String BANMANAGER = "banmanager";
    public static final String VANILLA = "vanilla";

    public static final Set<String> DATABASES = Set.of(SELF, LIBERTYBANS, LITEBANS, ADVANCEDBAN,
            BANMANAGER);

    public ImportSettings {
        databases = Map.copyOf(databases);
    }

    public Optional<Database> database(final String section) {
        return Optional.ofNullable(this.databases.get(section));
    }

    public record Database(String section, String jdbcUrl, String username, String password,
            String tablePrefix) {

        // The password must never reach a log.
        @Override
        public String toString() {
            return "Database[section=" + this.section + ", jdbcUrl=" + this.jdbcUrl
                    + ", username=" + this.username + ", password=***, tablePrefix="
                    + this.tablePrefix + "]";
        }
    }

    public record Vanilla(Path bannedPlayers, Path bannedIps) {
    }

}
