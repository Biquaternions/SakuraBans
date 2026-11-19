package net.serlith.sakurabans.core.model;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import net.serlith.sakurabans.api.model.Actor;
import net.serlith.sakurabans.api.model.EndKind;
import net.serlith.sakurabans.api.model.Lengths;
import net.serlith.sakurabans.api.model.PunishmentEnd;

public record SakuraPunishmentEnd(
        EndKind kind, Instant at, Optional<Actor> actor, Optional<String> reason)
        implements PunishmentEnd {

    private static final Set<EndKind> BY_NOBODY = Set.of(EndKind.EXPIRED, EndKind.SUPERSEDED);

    public SakuraPunishmentEnd {
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(at, "at");
        Objects.requireNonNull(actor, "actor");
        if (actor.isEmpty() && !BY_NOBODY.contains(kind)) {
            throw new IllegalArgumentException(kind + " needs an actor");
        }
        ModelChecks.text(reason, Lengths.MAX_REASON, "reason");
    }
}
