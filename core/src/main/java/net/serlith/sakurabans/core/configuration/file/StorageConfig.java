package net.serlith.sakurabans.core.configuration.file;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.Getter;
import net.serlith.sakurabans.core.configuration.settings.StorageType;
import net.serlith.sakurabans.core.configuration.settings.SyncMode;
import org.spongepowered.configurate.objectmapping.ConfigSerializable;

@Getter
@ConfigSerializable
public final class StorageConfig {

    private StorageType type = StorageType.H2;
    private H2Section h2 = new H2Section();
    private String host = "localhost";
    private int port;
    private String database = "sakurabans";
    private String username = "sakurabans";
    private String password = "";
    private String tablePrefix = "sb_";
    private String schema = "";
    private Map<String, String> properties = new LinkedHashMap<>();
    private PoolSection pool = new PoolSection();
    private SyncSection sync = new SyncSection();

    @Getter
    @ConfigSerializable
    public static final class H2Section {
        private String file = "sakurabans";
    }

    @Getter
    @ConfigSerializable
    public static final class PoolSection {
        private int maximumSize = 10;
        private int minimumIdle = 2;
        private Duration connectionTimeout = Duration.ofSeconds(5);
        private Duration maxLifetime = Duration.ofMinutes(30);
    }

    @Getter
    @ConfigSerializable
    public static final class SyncSection {
        private SyncMode mode = SyncMode.NONE;
        private Duration pollInterval = Duration.ofSeconds(2);
    }
}
