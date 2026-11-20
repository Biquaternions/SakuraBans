package net.serlith.sakurabans.core.event;

import net.serlith.sakurabans.api.event.PunishmentExpiredEvent;
import net.serlith.sakurabans.api.model.Punishment;

public record SakuraPunishmentExpiredEvent(Punishment punishment, boolean remote)
        implements PunishmentExpiredEvent {
}
