package net.serlith.sakurabans.core.configuration.file;

import java.util.LinkedHashMap;
import java.util.Map;
import lombok.Getter;
import org.spongepowered.configurate.objectmapping.ConfigSerializable;

@Getter
@ConfigSerializable
public final class LimitsConfig {

    private boolean reduceToLimit = true;
    private String consoleCooldown = "1s";
    private Map<String, GroupSection> groups = new LinkedHashMap<>();

    @Getter
    @ConfigSerializable
    public static final class GroupSection {

        private String permission = "";
        private Map<String, String> maxDuration = new LinkedHashMap<>();
        private Map<String, String> cooldown = new LinkedHashMap<>();
        private boolean requireTemplate;
        private String cooldownRedo = "1s";
    }
}
