package net.serlith.sakurabans.core.concurrent;

import java.util.concurrent.atomic.AtomicReference;

public final class Lifecycle {

    private final AtomicReference<LifecycleState> state =
            new AtomicReference<>(LifecycleState.NEW);

    public LifecycleState state() {
        return this.state.get();
    }

    public boolean running() {
        return this.state.get() == LifecycleState.RUNNING;
    }

    public void advance(final LifecycleState from, final LifecycleState to) {
        if (!this.state.compareAndSet(from, to)) {
            throw new IllegalStateException(
                    "can't go from " + this.state.get() + " to " + to + " (expected " + from + ")");
        }
    }

    public boolean advanceTo(final LifecycleState to) {
        while (true) {
            final LifecycleState current = this.state.get();
            if (current.compareTo(to) >= 0) {
                return false;
            }
            if (this.state.compareAndSet(current, to)) {
                return true;
            }
        }
    }

}
