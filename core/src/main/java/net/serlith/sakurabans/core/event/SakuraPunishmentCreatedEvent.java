package net.serlith.sakurabans.core.event;

import java.util.Optional;
import net.serlith.sakurabans.api.event.PunishmentCreatedEvent;
import net.serlith.sakurabans.api.model.Punishment;
import org.jspecify.annotations.Nullable;

public record SakuraPunishmentCreatedEvent(Punishment punishment,
        @Nullable Punishment supersededOrNull) implements PunishmentCreatedEvent {

    @Override
    public Optional<Punishment> superseded() {
        return Optional.ofNullable(this.supersededOrNull);
    }

}
