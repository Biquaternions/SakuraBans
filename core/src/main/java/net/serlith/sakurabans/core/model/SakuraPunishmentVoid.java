package net.serlith.sakurabans.core.model;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import net.serlith.sakurabans.api.model.Actor;
import net.serlith.sakurabans.api.model.Lengths;
import net.serlith.sakurabans.api.model.PunishmentVoid;

public record SakuraPunishmentVoid(Instant at, Actor actor, Optional<String> reason)
        implements PunishmentVoid {

    public SakuraPunishmentVoid {
        Objects.requireNonNull(at, "at");
        Objects.requireNonNull(actor, "actor");
        ModelChecks.text(reason, Lengths.MAX_REASON, "reason");
    }
}
