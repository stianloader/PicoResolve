package de.geolykt.picoresolve.repo;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URLConnection;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

import de.geolykt.picoresolve.internal.ConcurrencyUtil;

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
        if (connection instanceof HttpURLConnection httpUrlConn && (httpUrlConn.getResponseCode() / 100) != 2) {
            throw new IOException("Query for " + connection.getURL() + " returned with a response code of " + httpUrlConn.getResponseCode() + " (" + httpUrlConn.getResponseMessage() + ")");
        }
        return connection.getInputStream().readAllBytes();
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