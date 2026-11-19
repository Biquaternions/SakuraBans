package net.serlith.sakurabans.core.util;

import java.util.List;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;
import net.kyori.adventure.text.logger.slf4j.ComponentLogger;

public final class Futures {

    private Futures() {
    }

    public static <T> CompletableFuture<T> logged(final CompletableFuture<T> future,
            final ComponentLogger logger, final String what) {
        return future.whenComplete((value, failure) -> {
            if (failure != null) {
                final Throwable cause = unwrap(failure);
                if (!(cause instanceof CancellationException)) {
                    logger.error("Failed to {}", what, cause);
                }
            }
        });
    }

    public static <T> CompletableFuture<List<T>> allOf(final List<CompletableFuture<T>> futures) {
        final List<CompletableFuture<T>> copy = List.copyOf(futures);
        return CompletableFuture.allOf(copy.toArray(CompletableFuture[]::new))
                .thenApply(ignored -> copy.stream().map(CompletableFuture::join).toList());
    }

    public static Throwable unwrap(final Throwable failure) {
        Throwable current = failure;
        while ((current instanceof CompletionException || current instanceof ExecutionException)
                && current.getCause() != null) {
            current = current.getCause();
        }
        return current;
    }
}
