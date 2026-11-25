package net.serlith.sakurabans.core.configuration.file;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.Getter;
import org.spongepowered.configurate.objectmapping.ConfigSerializable;

@Getter
@ConfigSerializable
public final class WarningsSection {

    private String expireAfter = "30d";
    private Map<String, List<String>> actions = new LinkedHashMap<>();

}
