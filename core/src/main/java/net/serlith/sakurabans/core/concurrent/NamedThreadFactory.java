package net.serlith.sakurabans.core.concurrent;

import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

public final class NamedThreadFactory implements ThreadFactory {

    private final String prefix;
    private final boolean numbered;
    private final AtomicInteger counter = new AtomicInteger();

    public NamedThreadFactory(final String prefix, final boolean numbered) {
        this.prefix = prefix;
        this.numbered = numbered;
    }

    @Override
    public Thread newThread(final Runnable task) {
        final String name = this.numbered
                ? this.prefix + "-" + this.counter.incrementAndGet()
                : this.prefix;
        return Thread.ofPlatform().name(name).daemon(true).unstarted(task);
    }
}
