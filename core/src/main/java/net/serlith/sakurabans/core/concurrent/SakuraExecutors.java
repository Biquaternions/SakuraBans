package net.serlith.sakurabans.core.concurrent;

import java.time.Duration;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

public final class SakuraExecutors implements AutoCloseable {

    private final ExecutorService worker;
    private final ScheduledExecutorService timer;
    private final Consumer<Throwable> onTaskFailure;

    public SakuraExecutors(final Consumer<Throwable> onTaskFailure) {
        this.worker = Executors.newThreadPerTaskExecutor(
                Thread.ofVirtual().name("sakurabans-worker-", 1).factory());
        this.timer = Executors.newSingleThreadScheduledExecutor(
                new NamedThreadFactory("sakurabans-timer", false));
        this.onTaskFailure = onTaskFailure;
    }

    public ExecutorService worker() {
        return this.worker;
    }

    public ScheduledFuture<?> repeat(
            final Duration initial, final Duration period, final Runnable task) {
        final AtomicBoolean running = new AtomicBoolean();
        return this.timer.scheduleAtFixedRate(() -> {
            if (!running.compareAndSet(false, true)) {
                return;
            }
            try {
                this.worker.execute(() -> {
                    try {
                        task.run();
                    } catch (final RuntimeException | Error e) {
                        this.onTaskFailure.accept(e);
                    } finally {
                        running.set(false);
                    }
                });
            } catch (final RuntimeException e) {
                // Rejected because the worker is shutting down.
                running.set(false);
            }
        }, initial.toMillis(), period.toMillis(), TimeUnit.MILLISECONDS);
    }

    public ScheduledFuture<?> schedule(final Duration delay, final Runnable task) {
        return this.timer.schedule(() -> this.worker.execute(task), delay.toMillis(),
                TimeUnit.MILLISECONDS);
    }

    public boolean shutdown(final Duration timeout) {
        this.timer.shutdownNow();
        this.worker.shutdown();
        try {
            if (this.worker.awaitTermination(timeout.toMillis(), TimeUnit.MILLISECONDS)) {
                return true;
            }
        } catch (final InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        this.worker.shutdownNow();
        return false;
    }

    public boolean terminated() {
        return this.worker.isTerminated() && this.timer.isTerminated();
    }

    @Override
    public void close() {
        this.shutdown(Duration.ofSeconds(10));
    }
}
