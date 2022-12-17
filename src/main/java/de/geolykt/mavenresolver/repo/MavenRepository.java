package de.geolykt.mavenresolver.repo;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

public interface MavenRepository {

    CompletableFuture<RepositoryAttachedValue<byte[]>> getResource(String path, Executor executor);
    String getRepositoryId();
    String getPlaintextURL();

    /**
     * Obtains the interval between which cached resources from this repository should
     * get invalidated.
     *
     * @return The interval, in milliseconds
     */
    long getUpdateIntervall();
}
