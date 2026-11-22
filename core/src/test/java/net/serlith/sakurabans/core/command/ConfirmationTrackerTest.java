package net.serlith.sakurabans.core.command;

import java.time.Instant;
import java.util.concurrent.atomic.AtomicReference;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

class ConfirmationTrackerTest {

    private final AtomicReference<Instant> now =
            new AtomicReference<>(Instant.parse("2026-11-19T12:00:00Z"));
    private final ConfirmationTracker tracker = new ConfirmationTracker(this.now::get);

    @Test
    void confirmsTheSameCommandWithinTheWindowOnce() {
        Assertions.assertThat(this.tracker.confirm("Mod", "sb delete #1")).isFalse();

        this.tracker.request("Mod", "sb delete #1");
        Assertions.assertThat(this.tracker.confirm("Other", "sb delete #1")).isFalse();
        Assertions.assertThat(this.tracker.confirm("Mod", "sb delete #2")).isFalse();
        Assertions.assertThat(this.tracker.confirm("Mod", " sb delete #1 ")).isTrue();
        Assertions.assertThat(this.tracker.confirm("Mod", "sb delete #1")).isFalse();
    }

    @Test
    void lateConfirmationsDoNothing() {
        this.tracker.request("Mod", "sb delete #1");
        this.now.set(this.now.get().plus(ConfirmationTracker.WINDOW));

        Assertions.assertThat(this.tracker.confirm("Mod", "sb delete #1")).isFalse();
    }
}
