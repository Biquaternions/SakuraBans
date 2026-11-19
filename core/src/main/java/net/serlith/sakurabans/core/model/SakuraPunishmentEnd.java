package net.serlith.sakurabans.core.model;

import com.google.common.base.Preconditions;
import java.time.Instant;
import java.util.Optional;
import java.util.Set;
import net.serlith.sakurabans.api.model.Actor;
import net.serlith.sakurabans.api.model.EndKind;
import net.serlith.sakurabans.api.model.Lengths;
import net.serlith.sakurabans.api.model.PunishmentEnd;
import org.jspecify.annotations.Nullable;

public record SakuraPunishmentEnd(
        EndKind kind, Instant at, @Nullable Actor actorOrNull, @Nullable String reasonOrNull)
        implements PunishmentEnd {

    private static final Set<EndKind> BY_NOBODY = Set.of(EndKind.EXPIRED, EndKind.SUPERSEDED);

    public SakuraPunishmentEnd {
        Preconditions.checkNotNull(kind, "kind");
        Preconditions.checkNotNull(at, "at");
        if (actorOrNull == null && !BY_NOBODY.contains(kind)) {
            throw new IllegalArgumentException(kind + " needs an actor");
        }
        ModelChecks.optionalText(reasonOrNull, Lengths.MAX_REASON, "reason");
    }

    @Override
    public Optional<Actor> actor() {
        return Optional.ofNullable(this.actorOrNull);
    }

    @Override
    public Optional<String> reason() {
        return Optional.ofNullable(this.reasonOrNull);
    }
}
