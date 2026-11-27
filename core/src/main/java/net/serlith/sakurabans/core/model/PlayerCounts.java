package net.serlith.sakurabans.core.model;

import java.time.Instant;
import java.util.List;

public record PlayerCounts(long bans, long mutes, long shadowMutes, long warns, long kicks,
        long permanentActiveWarns, List<Instant> activeWarnExpiries) {

    public static final PlayerCounts NONE = new PlayerCounts(0, 0, 0, 0, 0, 0, List.of());

    public PlayerCounts {
        activeWarnExpiries = List.copyOf(activeWarnExpiries);
    }

    public long activeWarns(final Instant now) {
        return this.permanentActiveWarns
                + this.activeWarnExpiries.stream().filter(expiry -> expiry.isAfter(now)).count();
    }

    public long visibleMutes() {
        return this.mutes - this.shadowMutes;
    }
}
