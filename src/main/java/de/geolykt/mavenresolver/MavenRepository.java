package de.geolykt.mavenresolver;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

public interface MavenRepository {

    CompletableFuture<MavenResource> getResource(String path, Executor executor);
    String getRepositoryId();
    String getPlaintextURL();
    long getUpdateIntervall();
}
