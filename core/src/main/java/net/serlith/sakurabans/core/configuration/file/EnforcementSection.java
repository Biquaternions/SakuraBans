package net.serlith.sakurabans.core.configuration.file;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import net.serlith.sakurabans.api.model.AddressStrictness;
import org.spongepowered.configurate.objectmapping.ConfigSerializable;

@Getter
@ConfigSerializable
public final class EnforcementSection {

    private AddressStrictness addressStrictness = AddressStrictness.NORMAL;
    private boolean compositeByDefault;
    private List<String> muteCommandBlacklist = new ArrayList<>(List.of(
            "me", "say", "tell", "msg", "w", "whisper", "r", "reply", "mail", "minecraft:me",
            "minecraft:say", "minecraft:tell", "minecraft:msg", "minecraft:w"));
    private boolean enforceServerSwitch = true;
    private Duration loginCheckTimeout = Duration.ofSeconds(5);
}
