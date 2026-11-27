package net.serlith.sakurabans.core.limit;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.serlith.sakurabans.api.model.PunishmentType;
import org.jspecify.annotations.Nullable;

public final class CooldownTracker {

    private static final Duration REMEMBERED = Duration.ofDays(1);

    private record Key(UUID actor, PunishmentType type) {
    }

    private record VictimKey(String actor, String victim, String type) {
    }

    private final Map<Key, Instant> last = new ConcurrentHashMap<>();
    private final Cache<VictimKey, Instant> redo = cache();
    private final Cache<VictimKey, Instant> console = cache();

    private static Cache<VictimKey, Instant> cache() {
        return Caffeine.newBuilder().maximumSize(50_000).expireAfterWrite(REMEMBERED).build();
    }

    public void issued(final UUID actor, final PunishmentType type, final Instant at) {
        this.last.merge(new Key(actor, type), at, (old, now) -> now.isAfter(old) ? now : old);
    }

    public Duration remaining(final UUID actor, final PunishmentType type,
            final Duration cooldown, final Instant now) {
        return left(this.last.get(new Key(actor, type)), cooldown, now);
    }

    public void revokedOwn(final UUID actor, final String victim, final Instant at) {
        this.redo.put(new VictimKey(actor.toString(), victim, ""), at);
    }

    public Duration remainingRedo(final UUID actor, final String victim,
            final Duration cooldown, final Instant now) {
        return left(this.redo.getIfPresent(new VictimKey(actor.toString(), victim, "")),
                cooldown, now);
    }

    public void consolePunished(final String victim, final PunishmentType type,
            final Instant at) {
        this.console.put(new VictimKey("", victim, type.name()), at);
    }

    public Duration remainingConsole(final String victim, final PunishmentType type,
            final Duration cooldown, final Instant now) {
        return left(this.console.getIfPresent(new VictimKey("", victim, type.name())),
                cooldown, now);
    }

    private static Duration left(final @Nullable Instant previous, final Duration cooldown,
            final Instant now) {
        if (previous == null) {
            return Duration.ZERO;
        }
        final Duration left = Duration.between(now, previous.plus(cooldown));
        return left.isNegative() ? Duration.ZERO : left;
    }

}
