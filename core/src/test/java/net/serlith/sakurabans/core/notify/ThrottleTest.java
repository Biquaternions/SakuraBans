package net.serlith.sakurabans.core.notify;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicReference;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

class ThrottleTest {

    private final AtomicReference<Instant> now =
            new AtomicReference<>(Instant.parse("2026-11-19T12:00:00Z"));
    private final Throttle throttle = new Throttle(this.now::get);

    @Test
    void letsOneThroughPerIntervalAndKey() {
        final Duration interval = Duration.ofSeconds(30);

        Assertions.assertThat(this.throttle.tryAcquire("a", interval)).isTrue();
        Assertions.assertThat(this.throttle.tryAcquire("a", interval)).isFalse();
        Assertions.assertThat(this.throttle.tryAcquire("b", interval)).isTrue();

        this.now.set(this.now.get().plus(interval).minusMillis(1));
        Assertions.assertThat(this.throttle.tryAcquire("a", interval)).isFalse();
        this.now.set(this.now.get().plusMillis(1));
        Assertions.assertThat(this.throttle.tryAcquire("a", interval)).isTrue();
    }

    @Test
    void zeroIntervalsNeverThrottle() {
        Assertions.assertThat(this.throttle.tryAcquire("a", Duration.ZERO)).isTrue();
        Assertions.assertThat(this.throttle.tryAcquire("a", Duration.ZERO)).isTrue();
    }
}
