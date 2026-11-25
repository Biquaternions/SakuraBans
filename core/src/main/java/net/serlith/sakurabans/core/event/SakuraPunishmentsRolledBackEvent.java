package net.serlith.sakurabans.core.event;

import java.util.List;
import net.serlith.sakurabans.api.event.PunishmentsRolledBackEvent;
import net.serlith.sakurabans.api.model.Punishment;
import net.serlith.sakurabans.api.punish.RollbackRequest;

public record SakuraPunishmentsRolledBackEvent(RollbackRequest request,
        List<Punishment> punishments, boolean remote) implements PunishmentsRolledBackEvent {

    public SakuraPunishmentsRolledBackEvent {
        punishments = List.copyOf(punishments);
    }
}
