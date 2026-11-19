package net.serlith.sakurabans.core.model;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import net.serlith.sakurabans.api.model.Actor;
import net.serlith.sakurabans.api.model.Lengths;
import net.serlith.sakurabans.api.model.PunishmentVoid;
import org.jspecify.annotations.Nullable;

public record SakuraPunishmentVoid(Instant at, Actor actor, @Nullable String reasonOrNull)
        implements PunishmentVoid {

    public SakuraPunishmentVoid {
        Objects.requireNonNull(at, "at");
        Objects.requireNonNull(actor, "actor");
        ModelChecks.optionalText(reasonOrNull, Lengths.MAX_REASON, "reason");
    }

    @Override
    public Optional<String> reason() {
        return Optional.ofNullable(this.reasonOrNull);
    }
}
