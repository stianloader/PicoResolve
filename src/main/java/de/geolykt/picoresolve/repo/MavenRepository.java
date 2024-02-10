package de.geolykt.picoresolve.repo;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

public interface MavenRepository {

    @NotNull
    CompletableFuture<RepositoryAttachedValue<byte[]>> getResource(@NotNull String path, @NotNull Executor executor);

    @NotNull
    @Contract(pure = true)
    String getRepositoryId();

    @NotNull
    @Contract(pure = true)
    String getPlaintextURL();

    /**
     * Obtains the interval between which cached resources from this repository should
     * get invalidated.
     *
     * @return The interval, in milliseconds
     */
    @Contract(pure = true)
    long getUpdateIntervall();
}
