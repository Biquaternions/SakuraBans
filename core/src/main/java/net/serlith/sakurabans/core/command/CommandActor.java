package net.serlith.sakurabans.core.command;

import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import net.kyori.adventure.audience.Audience;
import net.serlith.sakurabans.api.model.Actor;
import net.serlith.sakurabans.api.model.ConsoleOperator;
import net.serlith.sakurabans.api.model.CustomOperator;
import net.serlith.sakurabans.api.model.Operator;
import net.serlith.sakurabans.api.model.PlayerOperator;

public interface CommandActor {

    String SOURCE_IN_GAME = "in-game";
    String SOURCE_CONSOLE = "console";

    Audience audience();

    String name();

    Optional<UUID> uuid();

    boolean console();

    boolean hasPermission(String node);

    Optional<Locale> locale();

    Optional<String> currentServer();

    default Operator asOperator() {
        if (this.console()) {
            return ConsoleOperator.INSTANCE;
        }
        return this.uuid()
                .<Operator>map(uuid -> PlayerOperator.of(uuid, this.name()))
                .orElseGet(() -> CustomOperator.of(this.name(), null));
    }

    default Actor asActor() {
        return Actor.of(this.asOperator(), this.console() ? SOURCE_CONSOLE : SOURCE_IN_GAME);
    }

}
