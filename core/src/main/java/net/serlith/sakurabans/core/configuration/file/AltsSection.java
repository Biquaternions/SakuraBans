package net.serlith.sakurabans.core.configuration.file;

import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import net.serlith.sakurabans.core.configuration.settings.AltAutoBan;
import org.spongepowered.configurate.objectmapping.ConfigSerializable;

@Getter
@ConfigSerializable
public final class AltsSection {

    private boolean registerOnConnection = true;
    private List<String> serversWithoutIpRegistration = new ArrayList<>(List.of("auth", "limbo"));
    private String recordExpiration = "never";
    private JoinNotificationSection joinNotification = new JoinNotificationSection();
    private List<String> hiddenPlayers = new ArrayList<>();
    private AltAutoBan autoBan = AltAutoBan.DISABLED;
    private int autoBanConfirmAbove = 5;

    @Getter
    @ConfigSerializable
    public static final class JoinNotificationSection {
        private boolean enabled = true;
        private boolean onlyIfPunished = true;
        private int threshold = 2;
        private int limit = 20;
    }
}
