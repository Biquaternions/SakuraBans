package net.serlith.sakurabans.core.configuration.file;

import lombok.Getter;
import org.spongepowered.configurate.objectmapping.ConfigSerializable;
import org.spongepowered.configurate.objectmapping.meta.Setting;

@Getter
@ConfigSerializable
public final class ScopesSection {

    @Setting("default")
    private String defaultScope = "global";
    private boolean requirePermissions;
}
