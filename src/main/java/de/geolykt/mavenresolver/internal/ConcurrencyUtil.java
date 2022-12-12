package de.geolykt.mavenresolver.internal;

import java.util.Objects;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.Supplier;

public class ConcurrencyUtil {

    public static <T> CompletableFuture<T> schedule(Callable<T> source, Executor executor) {
        Objects.requireNonNull(source);
        Objects.requireNonNull(executor);

        CompletableFuture<T> cf = new CompletableFuture<>();
        executor.execute(() -> {
            if (cf.isDone()) {
                return;
            }
            try {
                cf.complete(source.call());
            } catch (Throwable  t) {
                cf.completeExceptionally(t);
            }
        });
        return cf;
    }

    public static <T> CompletableFuture<T> configureFallback(CompletableFuture<T> mains, Supplier<CompletableFuture<T>> fallback) {
        // TODO do not absorb the root exception
        return mains.exceptionallyCompose((t) -> {
            return fallback.get();
        });
    }
}
