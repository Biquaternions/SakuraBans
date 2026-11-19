package net.serlith.sakurabans.core.model;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import net.serlith.sakurabans.api.model.EndKind;
import net.serlith.sakurabans.api.model.Lengths;
import net.serlith.sakurabans.api.model.Operator;
import net.serlith.sakurabans.api.model.PunishmentEnd;

public record SakuraPunishmentEnd(
        EndKind kind, Instant at, Optional<Operator> operator, Optional<String> reason)
        implements PunishmentEnd {

    public SakuraPunishmentEnd {
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(at, "at");
        Objects.requireNonNull(operator, "operator");
        if (kind == EndKind.REVOKED && operator.isEmpty()) {
            throw new IllegalArgumentException("a revocation needs an operator");
        }
        ModelChecks.text(reason, Lengths.MAX_REASON, "reason");
    }
}
