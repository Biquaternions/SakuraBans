package net.serlith.sakurabans.core.configuration.file;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import org.spongepowered.configurate.objectmapping.ConfigSerializable;

@Getter
@ConfigSerializable
public final class AltsSection {

    private boolean registerOnConnection = true;
    private List<String> serversWithoutIpRegistration = new ArrayList<>(List.of("auth", "limbo"));
    private String recordExpiration = "never";
    private JoinNotificationSection joinNotification = new JoinNotificationSection();
    private ConnectionLimiterSection connectionLimiter = new ConnectionLimiterSection();

    @Getter
    @ConfigSerializable
    public static final class JoinNotificationSection {
        private boolean enabled = true;
        private boolean onlyIfPunished = true;
        private int threshold = 2;
        private int limit = 20;
    }

    @Getter
    @ConfigSerializable
    public static final class ConnectionLimiterSection {
        private boolean enabled;
        private int limit = 5;
        private Duration window = Duration.ofMinutes(10);
    }
}
