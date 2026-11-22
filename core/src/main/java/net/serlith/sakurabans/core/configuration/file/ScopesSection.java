package net.serlith.sakurabans.core.configuration.file;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.Getter;
import org.spongepowered.configurate.objectmapping.ConfigSerializable;
import org.spongepowered.configurate.objectmapping.meta.Setting;

@Getter
@ConfigSerializable
public final class ScopesSection {

    @Setting("default")
    private String defaultScope = "global";
    private boolean requirePermissions;
    private Map<String, List<String>> categories = new LinkedHashMap<>();
}
