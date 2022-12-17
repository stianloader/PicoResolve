package de.geolykt.mavenresolver.repo;

import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

public interface DownloadNegotiator {

    public CompletableFuture<RepositoryAttachedValue<Path>> resolve(String path, Executor executor);
    public DownloadNegotiator addRepository(MavenRepository repo);
}
