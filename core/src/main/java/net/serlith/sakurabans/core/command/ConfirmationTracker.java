package net.serlith.sakurabans.core.command;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import java.time.Duration;
import java.time.Instant;
import java.time.InstantSource;
import java.util.concurrent.atomic.AtomicBoolean;

public final class ConfirmationTracker {

    public static final Duration WINDOW = Duration.ofSeconds(30);

    private final InstantSource clock;
    private final Cache<String, Instant> pending = Caffeine.newBuilder()
            .maximumSize(1_000)
            .expireAfterWrite(WINDOW.multipliedBy(2))
            .build();

    public ConfirmationTracker(final InstantSource clock) {
        this.clock = clock;
    }

    public void request(final String sender, final String command) {
        this.pending.put(key(sender, command), this.clock.instant());
    }

    public boolean confirm(final String sender, final String command) {
        final Instant now = this.clock.instant();
        final AtomicBoolean confirmed = new AtomicBoolean();
        this.pending.asMap().computeIfPresent(key(sender, command), (key, requested) -> {
            confirmed.set(now.isBefore(requested.plus(WINDOW)));
            return null;
        });
        return confirmed.get();
    }

    private static String key(final String sender, final String command) {
        return sender + '\u0000' + command.strip();
    }
}
