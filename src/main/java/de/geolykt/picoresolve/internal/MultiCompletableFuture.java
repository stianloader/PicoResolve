package de.geolykt.picoresolve.internal;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

public class MultiCompletableFuture<T> extends CompletableFuture<T> {

    static class MultiCompletionException extends CompletionException {

        private static final long serialVersionUID = -3361756801104382585L;

        public MultiCompletionException(Throwable[] causers) {
            for (Throwable t : causers) {
                if (t == null) {
                    continue;
                }
                this.addSuppressed(t);
            }
        }
    }

    private final CompletableFuture<T>[] sources;
    private final Throwable[] exceptions;
    private int exceptionalCompletions = 0;

    public MultiCompletableFuture(List<CompletableFuture<T>> sources) {
        this(sources.toArray(new CompletableFuture[0]));
    }

    public MultiCompletableFuture(CompletableFuture<T>[] sources) {
        this.sources = sources;
        this.exceptions = new Throwable[this.sources.length];
        for (int i = 0; i < sources.length; i++) {
            CompletableFuture<T> future = sources[i];
            future.thenAccept(this::sourceCompleted);
            final int futureIndex = i;
            future.exceptionally((ex) -> {
                this.sourceException(futureIndex, ex);
                return null;
            });
        }
    }

    private void sourceCompleted(T result) {
        if (this.complete(result)) {
            for (CompletableFuture<T> future : this.sources) {
                if (!future.isDone()) {
                    future.cancel(false);
                }
            }
        }
    }

    private void sourceException(int i, Throwable exception) {
        Objects.requireNonNull(exception);
        synchronized (this.exceptions) {
            if (this.exceptions[i] != null) {
                return;
            }
            this.exceptions[i] = exception;
            if (++exceptionalCompletions == this.exceptions.length) {
                if (!isDone()) {
                    completeExceptionally(new MultiCompletionException(exceptions).fillInStackTrace());
                }
            }
        }
    }
}
