package net.serlith.sakurabans.core.concurrent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class SakuraExecutorsTest {

    private final List<Throwable> failures = new CopyOnWriteArrayList<>();
    private final SakuraExecutors executors = new SakuraExecutors(this.failures::add);

    @AfterEach
    void shutdown() {
        this.executors.shutdown(Duration.ofSeconds(5));
    }

    private static List<Thread> timerThreads() {
        return Thread.getAllStackTraces().keySet().stream()
                .filter(thread -> thread.getName().equals("sakurabans-timer"))
                .toList();
    }

    @Test
    void workerThreadsAreNamedVirtualThreads() throws Exception {
        final Thread thread = this.executors.worker().submit(Thread::currentThread).get();

        assertThat(thread.isVirtual()).isTrue();
        assertThat(thread.getName()).startsWith("sakurabans-worker-");
    }

    @Test
    void theTimerIsOneDaemonPlatformThread() throws Exception {
        final CountDownLatch ran = new CountDownLatch(1);
        this.executors.schedule(Duration.ZERO, ran::countDown);
        assertThat(ran.await(5, TimeUnit.SECONDS)).isTrue();

        assertThat(timerThreads()).isNotEmpty()
                .allSatisfy(thread -> {
                    assertThat(thread.isDaemon()).isTrue();
                    assertThat(thread.isVirtual()).isFalse();
                });
    }

    @Test
    void scheduledWorkRunsOnTheWorker() throws Exception {
        final CountDownLatch ran = new CountDownLatch(1);
        final AtomicInteger virtual = new AtomicInteger();
        this.executors.schedule(Duration.ofMillis(10), () -> {
            virtual.set(Thread.currentThread().isVirtual() ? 1 : 0);
            ran.countDown();
        });

        assertThat(ran.await(5, TimeUnit.SECONDS)).isTrue();
        assertThat(virtual).hasValue(1);
    }

    @Test
    void repeatedRunsNeverOverlapAndSurviveFailures() throws Exception {
        final AtomicInteger concurrent = new AtomicInteger();
        final AtomicInteger maxConcurrent = new AtomicInteger();
        final CountDownLatch runs = new CountDownLatch(3);
        this.executors.repeat(Duration.ZERO, Duration.ofMillis(5), () -> {
            maxConcurrent.accumulateAndGet(concurrent.incrementAndGet(), Math::max);
            try {
                Thread.sleep(30);
            } catch (final InterruptedException e) {
                Thread.currentThread().interrupt();
            } finally {
                concurrent.decrementAndGet();
                runs.countDown();
            }
            throw new IllegalStateException("boom");
        });

        assertThat(runs.await(5, TimeUnit.SECONDS)).isTrue();
        assertThat(maxConcurrent).hasValue(1);
        assertThat(this.failures).isNotEmpty().allMatch(IllegalStateException.class::isInstance);
    }

    @Test
    void shutdownWaitsForRunningTasksAndStopsTheTimer() throws Exception {
        final CountDownLatch started = new CountDownLatch(1);
        final AtomicInteger finished = new AtomicInteger();
        this.executors.worker().execute(() -> {
            started.countDown();
            try {
                Thread.sleep(100);
            } catch (final InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            finished.incrementAndGet();
        });
        assertThat(started.await(5, TimeUnit.SECONDS)).isTrue();

        assertThat(this.executors.shutdown(Duration.ofSeconds(5))).isTrue();
        assertThat(finished).hasValue(1);
        assertThatThrownBy(() -> this.executors.worker().execute(() -> { }))
                .isInstanceOf(RejectedExecutionException.class);
        for (final Thread thread : timerThreads()) {
            thread.join(5_000);
            assertThat(thread.isAlive()).as("timer thread still running").isFalse();
        }
    }

    @Test
    void shutdownGivesUpAfterTheTimeout() throws Exception {
        final CountDownLatch started = new CountDownLatch(1);
        this.executors.worker().execute(() -> {
            started.countDown();
            try {
                Thread.sleep(10_000);
            } catch (final InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });
        assertThat(started.await(5, TimeUnit.SECONDS)).isTrue();

        assertThat(this.executors.shutdown(Duration.ofMillis(50))).isFalse();
    }
}
