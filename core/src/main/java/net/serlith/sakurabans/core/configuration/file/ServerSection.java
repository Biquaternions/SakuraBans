package net.serlith.sakurabans.core.configuration.file;

import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import net.serlith.sakurabans.core.configuration.settings.ServerRole;
import org.spongepowered.configurate.objectmapping.ConfigSerializable;

@Getter
@ConfigSerializable
public final class ServerSection {

    private String name = "auto";
    private List<String> categories = new ArrayList<>();
    private ServerRole role = ServerRole.AUTO;
}
