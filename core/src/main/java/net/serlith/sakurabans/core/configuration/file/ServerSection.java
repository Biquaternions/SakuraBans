package net.serlith.sakurabans.core.configuration.file;

import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import org.spongepowered.configurate.objectmapping.ConfigSerializable;

@Getter
@ConfigSerializable
public final class ServerSection {

    private String name = "";
    private List<String> categories = new ArrayList<>();
}
