package net.serlith.sakurabans.core.punish;

import net.serlith.sakurabans.api.model.Actor;
import net.serlith.sakurabans.api.model.Punishment;
import net.serlith.sakurabans.api.punish.PunishOptions;

public interface PunishLimits {

    PunishmentFactory.Prepared apply(PunishmentFactory.Prepared.Ready ready,
            PunishOptions options);

    void issued(Punishment punishment);

    void revoked(Punishment punishment, Actor actor);

}
