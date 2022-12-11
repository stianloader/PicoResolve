package de.geolykt.mavenresolver;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

public interface DownloadNegotiator {

    public CompletableFuture<MavenResource> resolve(String path, Executor executor);
    public DownloadNegotiator addRepository(MavenRepository repo);
}
