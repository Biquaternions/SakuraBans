package net.serlith.sakurabans.core.notify;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import java.time.Duration;
import java.time.Instant;
import java.time.InstantSource;
import java.util.concurrent.atomic.AtomicBoolean;

public final class Throttle {

    private final InstantSource clock;
    private final Cache<String, Instant> last = Caffeine.newBuilder()
            .maximumSize(10_000)
            .expireAfterWrite(Duration.ofHours(1))
            .build();

    public Throttle(final InstantSource clock) {
        this.clock = clock;
    }

    public boolean tryAcquire(final String key, final Duration interval) {
        final Instant now = this.clock.instant();
        final AtomicBoolean acquired = new AtomicBoolean();
        this.last.asMap().compute(key, (ignored, previous) -> {
            if (previous == null || !now.isBefore(previous.plus(interval))) {
                acquired.set(true);
                return now;
            }
            return previous;
        });
        return acquired.get();
    }

}
