package net.serlith.sakurabans.core.configuration.file;

import java.util.Map;
import lombok.Getter;
import org.spongepowered.configurate.objectmapping.ConfigSerializable;
import org.spongepowered.configurate.objectmapping.meta.Setting;

@Getter
@ConfigSerializable
public final class ReasonsSection {

    @Setting("default")
    private String defaultReason = "No reason specified";
    private RequireSection require = new RequireSection();
    private boolean allowNewlines;
    private Map<String, String> shortcuts = Map.of();

    @Getter
    @ConfigSerializable
    public static final class RequireSection {
        private boolean ban;
        private boolean mute;
        private boolean warn;
        private boolean kick;
        private boolean revoke;
    }
}
