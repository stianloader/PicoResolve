package org.stianloader.picoresolve.internal;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

import org.jetbrains.annotations.NotNull;

public final class JavaInterop {
    public static String codepointToString(int codepoint) {
        return new String(new int[] {codepoint}, 0, 1);
    }

    @SuppressWarnings("null")
    @NotNull
    public static <T> CompletableFuture<T> exceptionallyCompose(CompletableFuture<T> thiz, @NotNull Function<Throwable, CompletableFuture<T>> fn) {
        return thiz.handle((result, t) -> {
            if (t == null) {
                return thiz;
            } else {
                return fn.apply(t);
            }
        }).thenCompose(Function.identity());
    }

    @NotNull
    public static <T> CompletableFuture<T> failedFuture(@NotNull Throwable t) {
        CompletableFuture<T> cf = new CompletableFuture<>();
        cf.completeExceptionally(t);
        return cf;
    }

    public static byte @NotNull[] readAllBytes(InputStream is) throws IOException {
        byte[] buffer = new byte[4096];
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        for (int read = is.read(buffer); read != -1; read = is.read(buffer)) {
            baos.write(buffer, 0, read);
        }
        return baos.toByteArray();
    }

    private JavaInterop() {
        throw new AssertionError();
    }
}
