package org.stianloader.picoresolve.internal;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.atomic.AtomicInteger;

import org.stianloader.picoresolve.internal.MultiCompletableFuture.MultiCompletionException;

/**
 * A {@link CompletableFuture} that only completes when all futures complete,
 * exceptionally or not. The resulting future will only exceptionally complete if all
 * other futures complete exceptionally, otherwise it will complete normally once the last
 * future completes normally.
 */
public class StronglyMultiCompletableFuture<T> extends CompletableFuture<List<T>> {

    private final CompletableFuture<T>[] sources;
    private final T[] results;
    private final Throwable[] exceptions;
    private final AtomicInteger completions = new AtomicInteger();
    private int exceptionally = 0;

    public StronglyMultiCompletableFuture(List<CompletableFuture<T>> sources) {
        this(sources.toArray(new CompletableFuture[0]));
    }

    @SafeVarargs
    @SuppressWarnings("unchecked")
    public StronglyMultiCompletableFuture(CompletableFuture<T>... sources) {
        this.sources = sources;
        this.exceptions = new Throwable[this.sources.length];
        this.results = (T[]) new Object[this.sources.length];
        for (int i = 0; i < sources.length; i++) {
            CompletableFuture<T> future = sources[i];
            final int futureIndex = i;
            future.thenAccept((res) -> this.sourceCompleted(futureIndex, res));
            future.exceptionally((ex) -> {
                this.sourceException(futureIndex, ex);
                return null;
            });
        }
        if (sources.length == 0) {
            this.complete(new ArrayList<>());
        }
    }

    private void sourceCompleted(int i, T result) {
        Objects.requireNonNull(result);
        synchronized (this) {
            if (this.exceptions[i] != null || this.results[i] != null) {
                return;
            }
            this.results[i] = result;
            if (this.completions.incrementAndGet() == this.results.length && !this.isDone()) {
                List<T> results = new ArrayList<>();
                for (T t : this.results) {
                    if (t != null) {
                        results.add(t);
                    }
                }
                this.complete(results);
            }
        }
    }

    private void sourceException(int i, Throwable exception) {
        Objects.requireNonNull(exception);
        synchronized (this) {
            if (this.exceptions[i] != null || this.results[i] != null) {
                return;
            }
            this.exceptions[i] = exception;
            if (++this.exceptionally == this.exceptions.length) {
                if (!this.isDone()) {
                    completeExceptionally(this.generateException().fillInStackTrace());
                }
            }
            if (this.completions.incrementAndGet() == this.results.length && !this.isDone()) {
                List<T> results = new ArrayList<>();
                for (T t : this.results) {
                    if (t != null) {
                        results.add(t);
                    }
                }
                this.complete(results);
            }
        }
    }

    protected CompletionException generateException() {
        return new MultiCompletionException(this.exceptions);
    }

    public final void throwExceptionIfCompletedUncleanly() {
        if (this.exceptionally == 0) {
            return;
        }
        throw this.generateException();
    }
}
