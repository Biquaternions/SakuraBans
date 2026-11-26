package net.serlith.sakurabans.core.configuration.file;

import lombok.Getter;
import org.spongepowered.configurate.objectmapping.ConfigSerializable;

@Getter
@ConfigSerializable
public final class WebSection {

    private String url = "";
    private String token = "";
    private boolean liveApply = true;
}
