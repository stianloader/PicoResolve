package de.geolykt.mavenresolver.repo;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URLConnection;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

import de.geolykt.mavenresolver.internal.ConcurrencyUtil;

public class URIMavenRepository implements MavenRepository {

    private final URI base;
    private final String id;

    public URIMavenRepository(String id, URI base) {
        this.base = base;
        this.id = id;
    }

    protected byte[] getResource0(String path) throws Exception {
        URI resolved = base.resolve(path);
        URLConnection connection = resolved.toURL().openConnection();
        if (connection instanceof HttpURLConnection httpUrlConn && (httpUrlConn.getResponseCode() / 100) != 2) {
            throw new IOException("Query for " + connection.getURL() + " returned with a response code of " + httpUrlConn.getResponseCode() + " ( " + httpUrlConn.getResponseMessage() + ")");
        }
        return connection.getInputStream().readAllBytes();
    }

    @Override
    public CompletableFuture<RepositoryAttachedValue<byte[]>> getResource(String path, Executor executor) {
        return ConcurrencyUtil.schedule(() -> {
            return new RepositoryAttachedValue<>(getResource0(path), this);
        }, executor);
    }

    @Override
    public String getRepositoryId() {
        return this.id;
    }

    @Override
    public String getPlaintextURL() {
        return this.base.toString();
    }

    @Override
    public long getUpdateIntervall() {
        return 24 * 60 * 60 * 1000; // Once every day should be enough
    }
}
