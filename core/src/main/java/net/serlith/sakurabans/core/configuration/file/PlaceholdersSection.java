package net.serlith.sakurabans.core.configuration.file;

import java.time.Duration;
import lombok.Getter;
import org.spongepowered.configurate.objectmapping.ConfigSerializable;

@Getter
@ConfigSerializable
public final class PlaceholdersSection {

    private Duration refresh = Duration.ofMinutes(2);
}
