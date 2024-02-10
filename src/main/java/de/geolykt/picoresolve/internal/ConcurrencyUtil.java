package de.geolykt.picoresolve.internal;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.Function;
import java.util.function.Supplier;

import org.jetbrains.annotations.NotNull;

public class ConcurrencyUtil {

    @NotNull
    public static <T> CompletableFuture<T> schedule(@NotNull Callable<T> source, @NotNull Executor executor) {
        Objects.requireNonNull(source, "source may not be null");

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

    @SuppressWarnings("unchecked")
    private static <T extends Throwable> void sneakyThrow(Throwable t) throws T {
        throw (T) t;
    }

    public static <T> CompletableFuture<T> configureFallback(CompletableFuture<T> mains, Supplier<CompletableFuture<T>> fallback) {
        return mains.exceptionallyCompose((t) -> {
            return fallback.get().exceptionally((t2) -> {
                t2.addSuppressed(t);
                ConcurrencyUtil.sneakyThrow(t);
                throw new InternalError(t);
            });
        });
    }

    public static <T> CompletableFuture<T> exceptionally(CompletableFuture<T> main, Function<Throwable, T> fn) {
        return main.exceptionally((t) -> {
            T result = fn.apply(t);
            if (result == null) {
                ConcurrencyUtil.sneakyThrow(t);
                throw new InternalError();
            } else {
                return result;
            }
        });
    }

    @NotNull
    public static <T, C extends Collection<T>> CompletableFuture<C> thenAdd(@NotNull CompletableFuture<C> collectionProvider, @NotNull CompletableFuture<T> valueProvider) {
        @SuppressWarnings({"unchecked", "null"}) // Java generics really aren't the yellow of the egg as we Germans would put it
        StronglyMultiCompletableFuture<Collection<T>> cf = new StronglyMultiCompletableFuture<>((CompletableFuture<Collection<T>>) collectionProvider, valueProvider.thenApply(Collections::singleton));
        return cf.thenApply((list) -> {
            // StronglyMultiCompletableFuture can absorb exceptions. This isn't what we'd like to happen, so we will rethrow in case this occurred.
            if (list.size() != 2) {
                throw cf.generateException();
            }
            @SuppressWarnings("unchecked")
            C collection = (C) list.get(0);
            T addObject = ((List<T>) list.get(1)).get(0);
            collection.add(addObject);
            return collection;
        });
    }
}
