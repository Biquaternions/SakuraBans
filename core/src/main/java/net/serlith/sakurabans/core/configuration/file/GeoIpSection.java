package net.serlith.sakurabans.core.configuration.file;

import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import org.spongepowered.configurate.objectmapping.ConfigSerializable;

@Getter
@ConfigSerializable
public final class GeoIpSection {

    private boolean enabled;
    private String accountId = "";
    private String licenseKey = "";
    private String refresh = "7d";
    private List<String> blacklist = new ArrayList<>();
    private List<String> whitelist = new ArrayList<>();
    private boolean allowUnknown = true;
}
