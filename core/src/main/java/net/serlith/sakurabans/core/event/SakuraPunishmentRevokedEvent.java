package net.serlith.sakurabans.core.event;

import net.serlith.sakurabans.api.event.PunishmentRevokedEvent;
import net.serlith.sakurabans.api.model.Punishment;
import net.serlith.sakurabans.api.model.Visibility;

public record SakuraPunishmentRevokedEvent(Punishment punishment, Visibility visibility,
        boolean remote) implements PunishmentRevokedEvent {
}
