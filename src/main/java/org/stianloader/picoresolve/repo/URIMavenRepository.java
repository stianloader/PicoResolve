package org.stianloader.picoresolve.repo;

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URLConnection;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.stianloader.picoresolve.internal.ConcurrencyUtil;
import org.stianloader.picoresolve.internal.JavaInterop;

public class URIMavenRepository implements MavenRepository {

    @NotNull
    private final URI base;
    @NotNull
    private final String id;

    public URIMavenRepository(@NotNull String id, @NotNull URI base) {
        if (base.getPath().isEmpty()) {
            base = base.resolve("/");
        } else if (!base.getPath().endsWith("/")) {
            base = base.resolve(base.getPath() + "/");
        }
        this.base = base;
        this.id = id;
    }

    protected byte @NotNull[] getResource0(@NotNull String path) throws Exception {
        URI resolved = this.base.resolve(path);
        System.out.println("Downloading " + resolved);
        URLConnection connection = resolved.toURL().openConnection();
        if (connection instanceof HttpURLConnection) {
            HttpURLConnection httpUrlConn = (HttpURLConnection) connection;
            if ((httpUrlConn.getResponseCode() / 100) != 2) {
                throw new IOException("Query for " + connection.getURL() + " returned with a response code of " + httpUrlConn.getResponseCode() + " (" + httpUrlConn.getResponseMessage() + ")");
            }
        }

        try (InputStream is = connection.getInputStream()) {
            return JavaInterop.readAllBytes(is);
        }
    }

    @Override
    @NotNull
    public CompletableFuture<RepositoryAttachedValue<byte[]>> getResource(@NotNull String path, @NotNull Executor executor) {
        return ConcurrencyUtil.schedule(() -> {
            return new RepositoryAttachedValue<>(this, this.getResource0(path));
        }, executor);
    }

    @Override
    @NotNull
    @Contract(pure = true)
    public String getRepositoryId() {
        return this.id;
    }

    @Override
    @NotNull
    @Contract(pure = true)
    public String getPlaintextURL() {
        return this.base.toString();
    }

    @Override
    @Contract(pure = true)
    public long getUpdateIntervall() {
        return 24 * 60 * 60 * 1000; // Once every day should be enough
    }
}
