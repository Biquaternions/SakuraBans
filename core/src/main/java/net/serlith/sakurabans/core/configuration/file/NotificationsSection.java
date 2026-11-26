package net.serlith.sakurabans.core.configuration.file;

import lombok.Getter;
import org.spongepowered.configurate.objectmapping.ConfigSerializable;

@Getter
@ConfigSerializable
public final class NotificationsSection {

    private String attemptThrottle = "30s";

}
