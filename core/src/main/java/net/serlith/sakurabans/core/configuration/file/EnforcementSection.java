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
            "me", "say", "tell", "msg", "w", "whisper", "r", "reply", "mail"));
    private boolean enforceServerSwitch = true;
    private Duration loginCheckTimeout = Duration.ofSeconds(5);
    private List<String> kickIgnoredReasons = new ArrayList<>(List.of("restarting", "afk"));
    private List<String> serversWithoutLoginCheck = new ArrayList<>();
    private boolean loginCheck = true;
    private boolean networkKick;
    private String ipBanDecay = "never";
}
