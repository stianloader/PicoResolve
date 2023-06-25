package de.geolykt.picoresolve.repo;

import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

/**
 * The purpose of a repository negotiator is to negotiate which repository should be used
 * when a file is requested. Furthermore caching and file locking fall under the tasks performed
 * by the negotiator.
 */
public interface RepositoryNegotiatior {

    /**
     * Resolve a "standard" non-metadata file from the repositories and store it into a file.
     *
     * <p><ul>
     * <li>Exceptional completion CAN occur if a maven-metadata.xml is requested.</li>
     * <li>The returned {@link CompletableFuture} WILL complete exceptionally if the requested file does not exist
     * in any repository known to this instance of the {@link RepositoryNegotiatior} and if the file was not
     * cached locally beforehand.</li>
     * <li>The {@link CompletableFuture} CAN complete exceptionally if the requested file does not exist in
     * the repositories but was cached locally previously.</li>
     * <li>However it SHOULD complete normally if the file was cached recently - that is before the repository
     * refresh interval.</li>
     * <li>Normal completion MUST occur if any repository contains the file. (It may complete exceptionally if
     * needed file IO is not possible for the caching, but that is an ignored edge-case scenario here)</li>
     * <li>Normal completion SHOULD occur if the file exists locally at the right path, even though it was never
     * stored beforehand through caching mechanism known to the negotiator. More specifically, certain internal
     * metadata files used by the negotiator can be absent.</li>
     * </ul>
     *
     * <p>Reason as to why maven-metadata.xml and other files have their own resolve methods is that
     * MavenLocal stores metadata files a bit different to non-metadata files and therefore it makes sense
     * to separate the two methods.
     *
     * @param path The path relative to the repository root where the file is located.
     * @param executor The executor with whom asynchronous operations should be performed.
     * @return A {@link CompletableFuture} which upon non-exceptional {@link CompletableFuture#isDone() completion}
     * stores the path where the resolved file is stored locally.
     */
    public CompletableFuture<RepositoryAttachedValue<Path>> resolveStandard(String path, Executor executor);

    /**
     * Resolve all relevant maven-metadata.xml files from the repositories and cache them into files.
     *
     * <p><ul>
     * <li>This method MUST throw directly if something else but a maven-metadata.xml is requested.</li>
     * <li>The returned {@link CompletableFuture} WILL complete exceptionally if the requested file does not exist
     * in any repository known to this instance of the {@link RepositoryNegotiatior} and if the file was not
     * cached locally beforehand.</li>
     * <li>The {@link CompletableFuture} CAN complete exceptionally if the requested file does not exist in
     * the repositories but was cached locally previously.</li>
     * <li>However it SHOULD complete normally if the file was cached recently - that is before the repository
     * refresh interval.</li>
     * <li>Normal completion MUST occur if any repository contains the file. (It may complete exceptionally if
     * needed file IO is not possible for the caching, but that is an ignored edge-case scenario here)</li>
     * <li>Normal completion SHOULD occur if the file exists locally at the right path, even though it was never
     * stored beforehand through caching mechanism known to the negotiator. More specifically, certain internal
     * metadata files used by the negotiator can be absent.</li>
     * </ul>
     *
     * <p>Reason as to why maven-metadata.xml and other files have their own resolve methods is that
     * MavenLocal stores metadata files a bit different to non-metadata files and therefore it makes sense
     * to separate the two methods.
     *
     * <p>Furthermore it makes sense to basically merge all the maven-metadata.xml files instead
     * of only fetching a single one.
     *
     * @param path The path relative to the repository root where the file is located.
     * @param executor The executor with whom asynchronous operations should be performed.
     * @throws IllegalArgumentException If the requested file is not a maven-metadata.xml.
     * @return A {@link CompletableFuture} which upon non-exceptional {@link CompletableFuture#isDone() completion}
     * stores the path where the resolved file is stored locally.
     */
    public CompletableFuture<List<RepositoryAttachedValue<Path>>> resolveMavenMeta(String path, Executor executor);

    public RepositoryNegotiatior addRepository(MavenRepository repo);
}
