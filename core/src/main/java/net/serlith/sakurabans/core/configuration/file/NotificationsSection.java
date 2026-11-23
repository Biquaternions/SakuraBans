package net.serlith.sakurabans.core.configuration.file;

import java.time.Duration;
import lombok.Getter;
import org.spongepowered.configurate.objectmapping.ConfigSerializable;

@Getter
@ConfigSerializable
public final class NotificationsSection {

    private Duration attemptThrottle = Duration.ofSeconds(30);
    private boolean sendToPlayers = true;

}
