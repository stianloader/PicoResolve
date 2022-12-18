package de.geolykt.mavenresolver.repo;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

import de.geolykt.mavenresolver.internal.MultiCompletableFuture;
import de.geolykt.mavenresolver.internal.StronglyMultiCompletableFuture;
import de.geolykt.mavenresolver.internal.meta.LastUpdatedFile;
import de.geolykt.mavenresolver.internal.meta.RemoteRepositoryProperties;
import de.geolykt.mavenresolver.internal.meta.ResolverMetaStatus;

/**
 * An implementation of the {@link RepositoryNegotiatior} interface capable to reading and writing maven locals
 * while having full compatibility with the latest versions of standard maven resolver
 * (as of time of writing, so late 2022). This includes file locking and internal metadata files,
 * which were reverse-engineered by looking at file access patterns that became apparent when inspecting
 * file IO with tools such as inotifywait.
 */
public class MavenLocalRepositoryNegotiator implements RepositoryNegotiatior {
    private final Path mavenLocal;
    private final Set<String> remoteIds = new HashSet<>();
    private final List<MavenRepository> remoteRepositories = new ArrayList<>();

    public MavenLocalRepositoryNegotiator(Path mavenLocal) {
        if (mavenLocal == null) {
            throw new IllegalArgumentException("The cache directory defined by \"mavenLocal\" may not be null!");
        }
        this.mavenLocal = mavenLocal;
        if (!Files.isDirectory(mavenLocal)) {
            if (Files.notExists(mavenLocal)) {
                try {
                    Files.createDirectories(mavenLocal);
                } catch (IOException e) {
                    throw new IllegalStateException("The \"mavenLocal\" argument must point to a directory. However this constructor was unable to make \"" + mavenLocal.toAbsolutePath() + "\" a directory.", e);
                }
            } else {
                throw new IllegalArgumentException("The \"mavenLocal\" argument must point to a directory. It currently points to " + mavenLocal.toAbsolutePath());
            }
        }
    }

    public Path getLocalCache() {
        return this.mavenLocal;
    }

    public MavenLocalRepositoryNegotiator addRepository(MavenRepository remote) {
        if (this.remoteIds.add(remote.getRepositoryId())) {
            this.remoteRepositories.add(remote);
        } else {
            throw new IllegalStateException("There is already a repository with the id \"" + remote.getRepositoryId() + "\" registered!");
        }
        return this;
    }

    @Override
    public CompletableFuture<RepositoryAttachedValue<Path>> resolveStandard(String path, Executor executor) {

        Path localFile = this.mavenLocal.resolve(path);
        Path lastUpdateFile = this.mavenLocal.resolve(path + ".lastUpdated");
        Path remoteRepos = localFile.resolveSibling("_remote.repositories");

        boolean localFilePresent = Files.exists(localFile);

        RemoteRepositoryProperties repoProps = RemoteRepositoryProperties.tryRead(remoteRepos);
        Optional<String> sourceRepo = repoProps.getSourceRepository(localFile.getFileName().toString());

        if (localFilePresent && sourceRepo.isEmpty()) {
            // Maven local
            return CompletableFuture.completedFuture(new RepositoryAttachedValue<>(localFile, null));
        }
        if (!localFilePresent && Files.notExists(localFile.getParent())) {
            try {
                Files.createDirectories(localFile.getParent());
            } catch (IOException ignored) {
            }
        }

        LastUpdatedFile lastUpdated = LastUpdatedFile.tryParse(lastUpdateFile);

        List<MavenRepository> candidateRepositories = new ArrayList<>();

        for (MavenRepository remote : this.remoteRepositories) {
            Long lastFetch = lastUpdated.getLastFetchTime(remote.getPlaintextURL());
            if (sourceRepo.isPresent() && remote.getRepositoryId().equals(sourceRepo.get())) {
                if (lastFetch == null) {
                    // Either this is not actually the origin repository or the maven
                    // resolver did something strange here (for some reason it is rather common for the .lastUpdated
                    // file to be absent).
                    // Whatever the reason, there is no need to fetch the file from remote again
                    return CompletableFuture.completedFuture(new RepositoryAttachedValue<>(localFile, remote));
                } else if ((lastFetch + remote.getUpdateIntervall()) > System.currentTimeMillis()) {
                    // The cache is still valid - no need to fetch!
                    return CompletableFuture.completedFuture(new RepositoryAttachedValue<>(localFile, remote));
                } else {
                    candidateRepositories.clear();
                    candidateRepositories.add(remote);
                    break;
                }
            }
            if (!lastUpdated.hasErrored(remote.getPlaintextURL())) {
                if (lastFetch != null && (lastFetch + remote.getUpdateIntervall()) > System.currentTimeMillis()) {
                    return CompletableFuture.completedFuture(new RepositoryAttachedValue<>(localFile, remote));
                }
            } else if (lastFetch != null && (lastFetch + remote.getUpdateIntervall()) > System.currentTimeMillis()) {
                continue;
            }
            candidateRepositories.add(remote);
        }

        if (candidateRepositories.isEmpty() && localFilePresent) {
            return CompletableFuture.completedFuture(new RepositoryAttachedValue<>(localFile, null));
        }

        List<CompletableFuture<RepositoryAttachedValue<byte[]>>> futures = new ArrayList<>();
        for (MavenRepository remote : candidateRepositories) {
            CompletableFuture<RepositoryAttachedValue<byte[]>> future = remote.getResource(path, executor).exceptionally((ex) -> {
                lastUpdated.updateEntryErrored(remote.getPlaintextURL(), "", System.currentTimeMillis());
                return null;
            });
            future.thenRun(() -> {
                lastUpdated.updateEntrySuccess(remote.getPlaintextURL(), System.currentTimeMillis());
            });
            futures.add(future);
            if (future.isDone() && !future.isCompletedExceptionally()) {
                break; // Let's note waste too much CPU time when running with a synchronous executor
            }
        }
        CompletableFuture<RepositoryAttachedValue<byte[]>> combined = new MultiCompletableFuture<>(futures);

        CompletableFuture<RepositoryAttachedValue<Path>> ret = combined.thenApply((rav) -> {
            write(rav.getValue(), localFile);
            repoProps.setSourceRepository(localFile.getFileName().toString(), rav.getRepository().getRepositoryId());
            repoProps.tryWrite(remoteRepos);
            return new RepositoryAttachedValue<>(localFile, rav.getRepository());
        }).exceptionally((ex) -> {
            if (Files.exists(localFile)) {
                return new RepositoryAttachedValue<>(localFile, null);
            }
            return null;
        });
        ret.thenRun(() -> {
            try {
                lastUpdated.write(lastUpdateFile);
            } catch (Throwable ignored) {
            }
        });
        return ret;
    }

    protected void write(byte[] data, Path to) {
        FileLock fileLock = null;
        try {
            Path parts = to.resolveSibling(to.getFileName().toString() + ".part");
            Path lock = to.resolveSibling(to.getFileName().toString() + ".part.lock");

            FileChannel lockChannel = FileChannel.open(lock, StandardOpenOption.WRITE, StandardOpenOption.CREATE, StandardOpenOption.DELETE_ON_CLOSE);
            long idleTime = 0L;
            while ((fileLock = lockChannel.tryLock()) == null) {
                try {
                    Thread.sleep(10L);
                } catch (InterruptedException ignored) {
                }
                if ((idleTime += 10L) > 10_000L) {
                    throw new IOException("Waited more than 10 seconds to acquire lock on " + parts.toAbsolutePath());
                }
            }

            Files.write(parts, data, StandardOpenOption.CREATE_NEW);
            Files.move(parts, to, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);

            fileLock.release();
            fileLock.acquiredBy().close();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        } finally {
            if (fileLock != null) {
                try {
                    fileLock.release();
                    fileLock.acquiredBy().close();
                } catch (IOException ignored) {
                }
            }
        }
    }

    @Override
    public CompletableFuture<List<RepositoryAttachedValue<Path>>> resolveMavenMeta(String path, Executor executor) {
        Path parentDirectory = this.mavenLocal.resolve(path + "/..");
        Path resolverProperties = parentDirectory.resolve("resolver-status.properties");

        if (!path.endsWith("/maven-metadata.xml")) {
            throw new IllegalArgumentException("This method may not be used to resolve anything but maven-metadata.xml (although it may be in various folders). Instead \"" + path + "\" was used as an input.");
        }

        List<CompletableFuture<RepositoryAttachedValue<Path>>> futures = new ArrayList<>();

        if (!Files.exists(parentDirectory)) {
            try {
                Files.createDirectories(parentDirectory);
            } catch (IOException ignored) {
            }
        } else {
            Path mvnLocalMeta = parentDirectory.resolve("maven-metadata-local.xml");
            if (Files.exists(mvnLocalMeta)) {
                futures.add(CompletableFuture.completedFuture(new RepositoryAttachedValue<>(mvnLocalMeta, null)));
            }
        }

        ResolverMetaStatus resolverStatus = ResolverMetaStatus.tryParse(resolverProperties);

        for (MavenRepository remote : this.remoteRepositories) {
            Path localFile = parentDirectory.resolve("maven-metadata-" + remote.getRepositoryId() + ".xml");
            Long lastFetch = resolverStatus.getLastFetchTime(remote.getRepositoryId());

            if (lastFetch != null && (lastFetch + remote.getUpdateIntervall()) > System.currentTimeMillis()) {
                if (resolverStatus.hasErrored(path)) {
                    continue;
                } else if (Files.exists(localFile)) {
                    // The cache is still valid - no need to fetch!
                    futures.add(CompletableFuture.completedFuture(new RepositoryAttachedValue<>(localFile, remote)));
                    continue;
                }
            }
            CompletableFuture<RepositoryAttachedValue<Path>> future = remote.getResource(path, executor).exceptionally((ex) -> {
                resolverStatus.updateEntryErrored(remote.getRepositoryId(), "", System.currentTimeMillis());
                return null;
            }).thenApply((rav) -> {
                resolverStatus.updateEntrySuccess(remote.getRepositoryId(), System.currentTimeMillis());
                write(rav.getValue(), localFile);
                return new RepositoryAttachedValue<>(localFile, rav.getRepository());
            });
            futures.add(future);
        }

        if (futures.isEmpty()) {
            return CompletableFuture.failedFuture(new IllegalStateException("RepositoryNegotiator has exhausted all available repositories").fillInStackTrace());
        }

        CompletableFuture<List<RepositoryAttachedValue<Path>>> ret = new StronglyMultiCompletableFuture<>(futures);
        ret.thenRun(() -> {
            try {
                resolverStatus.write(resolverProperties);
            } catch (Throwable ignored) {
            }
        });
        return ret;
    }
}
