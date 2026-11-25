package net.serlith.sakurabans.core.limit;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.serlith.sakurabans.api.model.PunishmentType;

public final class CooldownTracker {

    private record Key(UUID actor, PunishmentType type) {
    }

    private final Map<Key, Instant> last = new ConcurrentHashMap<>();

    public void issued(final UUID actor, final PunishmentType type, final Instant at) {
        this.last.merge(new Key(actor, type), at, (old, now) -> now.isAfter(old) ? now : old);
    }

    public Duration remaining(final UUID actor, final PunishmentType type,
            final Duration cooldown, final Instant now) {
        final Instant previous = this.last.get(new Key(actor, type));
        if (previous == null) {
            return Duration.ZERO;
        }
        final Duration left = Duration.between(now, previous.plus(cooldown));
        return left.isNegative() ? Duration.ZERO : left;
    }

}
