package net.serlith.sakurabans.core.configuration.file;

import lombok.Getter;
import org.spongepowered.configurate.objectmapping.ConfigSerializable;

@Getter
@ConfigSerializable
public final class ImportConfig {

    private DatabaseSection self = database("", "sakurabans", "", "sb_");
    private DatabaseSection libertybans = database("jdbc:hsqldb:file:plugins/LibertyBans/internal"
            + "/hypersql/punishments-database;hsqldb.lock_file=false", "SA", "", "libertybans_");
    private DatabaseSection litebans = database("jdbc:h2:./plugins/LiteBans/litebans", "", "",
            "litebans_");
    private DatabaseSection advancedban = database(
            "jdbc:hsqldb:file:plugins/AdvancedBan/data/storage;hsqldb.lock_file=false", "SA", "",
            "");
    private DatabaseSection banmanager = database("jdbc:h2:./plugins/BanManager/local-bans", "",
            "", "bm_");
    private VanillaSection vanilla = new VanillaSection();

    @Getter
    @ConfigSerializable
    public static final class DatabaseSection {
        private String jdbcUrl = "";
        private String username = "";
        private String password = "";
        private String tablePrefix = "";

        // The password must never reach a log.
        @Override
        public String toString() {
            return "DatabaseSection[jdbcUrl=" + this.jdbcUrl + ", username=" + this.username
                    + ", password=***, tablePrefix=" + this.tablePrefix + "]";
        }
    }

    @Getter
    @ConfigSerializable
    public static final class VanillaSection {
        private String bannedPlayers = "banned-players.json";
        private String bannedIps = "banned-ips.json";
    }

    private static DatabaseSection database(final String jdbcUrl, final String username,
            final String password, final String tablePrefix) {
        final DatabaseSection section = new DatabaseSection();
        section.jdbcUrl = jdbcUrl;
        section.username = username;
        section.password = password;
        section.tablePrefix = tablePrefix;
        return section;
    }
}
