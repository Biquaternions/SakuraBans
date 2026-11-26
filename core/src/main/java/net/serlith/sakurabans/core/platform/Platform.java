package net.serlith.sakurabans.core.platform;

import java.util.Collection;
import java.util.Optional;
import java.util.UUID;
import net.kyori.adventure.audience.Audience;

public interface Platform {

    TaskScheduler scheduler();

    Optional<OnlinePlayer> player(UUID uuid);

    Collection<? extends OnlinePlayer> onlinePlayers();

    Audience console();

    void dispatchConsoleCommand(String command);

    ServerIdentity serverIdentity();

    ProfileLookup profiles();

    boolean onlineMode();

    /** The platform's family, as the editor payload names it: {@code paper} or {@code velocity}. */
    String platformName();

    String platformVersion();
}
