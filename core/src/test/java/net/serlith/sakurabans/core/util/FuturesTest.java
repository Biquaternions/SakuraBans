package net.serlith.sakurabans.core.util;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;
import net.kyori.adventure.text.logger.slf4j.ComponentLogger;
import org.junit.jupiter.api.Test;

class FuturesTest {

    private final List<Object[]> errors = new ArrayList<>();
    private final ComponentLogger logger = (ComponentLogger) Proxy.newProxyInstance(
            ComponentLogger.class.getClassLoader(), new Class<?>[] {ComponentLogger.class},
            (proxy, method, args) -> {
                if (method.getName().equals("error")) {
                    this.errors.add(args);
                }
                return method.getReturnType() == boolean.class ? Boolean.TRUE : null;
            });

    @Test
    void logsAnUnexpectedFailureOnceWithTheUnwrappedCause() {
        final IllegalStateException cause = new IllegalStateException("db down");
        final CompletableFuture<String> failing = CompletableFuture.supplyAsync(() -> {
            throw cause;
        });

        final CompletableFuture<String> logged = Futures.logged(failing, this.logger, "load bans");

        assertThat(logged).failsWithin(java.time.Duration.ofSeconds(5));
        assertThat(this.errors).singleElement().satisfies(args -> {
            assertThat(args[0]).isEqualTo("Failed to {}");
            assertThat(args[1]).isEqualTo("load bans");
            assertThat(args[2]).isSameAs(cause);
        });
    }

    @Test
    void cancellationAndSuccessAreNotLogged() {
        final CompletableFuture<String> cancelled = new CompletableFuture<>();
        Futures.logged(cancelled, this.logger, "wait");
        cancelled.cancel(false);
        Futures.logged(CompletableFuture.completedFuture("ok"), this.logger, "succeed");

        assertThat(this.errors).isEmpty();
    }

    @Test
    void allOfKeepsTheInputOrder() {
        final CompletableFuture<Integer> slow = new CompletableFuture<>();
        final CompletableFuture<List<Integer>> all =
                Futures.allOf(List.of(slow, CompletableFuture.completedFuture(2)));
        slow.complete(1);

        assertThat(all.join()).containsExactly(1, 2);
        assertThat(Futures.allOf(List.<CompletableFuture<Integer>>of()).join()).isEmpty();
    }

    @Test
    void allOfFailsWithTheFirstFailure() {
        final CompletableFuture<List<Integer>> all = Futures.allOf(List.of(
                CompletableFuture.completedFuture(1),
                CompletableFuture.failedFuture(new IllegalArgumentException("bad"))));

        assertThat(all).isCompletedExceptionally();
    }

    @Test
    void unwrapStripsWrappers() {
        final IllegalStateException cause = new IllegalStateException();

        assertThat(Futures.unwrap(new CompletionException(new ExecutionException(cause))))
                .isSameAs(cause);
        assertThat(Futures.unwrap(cause)).isSameAs(cause);
        final CompletionException empty = new CompletionException("no cause", null);
        assertThat(Futures.unwrap(empty)).isSameAs(empty);
    }
}
