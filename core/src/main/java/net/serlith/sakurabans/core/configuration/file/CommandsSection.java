package net.serlith.sakurabans.core.configuration.file;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.Getter;
import org.spongepowered.configurate.objectmapping.ConfigSerializable;

@Getter
@ConfigSerializable
public final class CommandsSection {

    private Map<String, List<String>> labels = new LinkedHashMap<>(Map.of(
            "sakurabans", List.of("sakurabans", "sb")));
}
