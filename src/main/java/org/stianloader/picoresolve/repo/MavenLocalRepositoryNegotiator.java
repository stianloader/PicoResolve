package org.stianloader.picoresolve.repo;

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
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.stianloader.picoresolve.internal.ConcurrencyUtil;
import org.stianloader.picoresolve.internal.JavaInterop;
import org.stianloader.picoresolve.internal.MultiCompletableFuture;
import org.stianloader.picoresolve.internal.StronglyMultiCompletableFuture;
import org.stianloader.picoresolve.internal.meta.LastUpdatedFile;
import org.stianloader.picoresolve.internal.meta.RemoteRepositoryProperties;
import org.stianloader.picoresolve.internal.meta.ResolverMetaStatus;

/**
 * An implementation of the {@link RepositoryNegotiatior} interface capable to reading and writing maven locals
 * while having full compatibility with the latest versions of the standard maven resolver
 * (as of time of writing, so late 2022). This includes support for file locking and internal metadata files,
 * which were reverse-engineered by looking at file access patterns that became apparent when inspecting
 * file IO with tools such as inotifywait.
 */
public class MavenLocalRepositoryNegotiator implements RepositoryNegotiatior {
    @NotNull
    private final Path mavenLocal;
    @NotNull
    private final Set<String> remoteIds = new HashSet<>();
    @NotNull
    private final List<MavenRepository> remoteRepositories = new ArrayList<>();
    private boolean writeMetadata = true;

    public MavenLocalRepositoryNegotiator(@NotNull Path mavenLocal) {
        this.mavenLocal = Objects.requireNonNull(mavenLocal, "The cache directory defined by \"mavenLocal\" may not be null!");
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

    @NotNull
    @Contract(mutates = "this", pure = false, value = "null -> fail; !null -> this")
    public MavenLocalRepositoryNegotiator addRepository(@NotNull MavenRepository remote) {
        if (this.remoteIds.add(remote.getRepositoryId())) {
            this.remoteRepositories.add(remote);
        } else {
            throw new IllegalStateException("There is already a repository with the id \"" + remote.getRepositoryId() + "\" registered!");
        }
        return this;
    }

    @NotNull
    public Path getLocalCache() {
        return this.mavenLocal;
    }

    @Override
    @NotNull
    public CompletableFuture<List<RepositoryAttachedValue<Path>>> resolveMavenMeta(@NotNull String path, @NotNull Executor executor) {
        Path parentDirectory = this.mavenLocal.resolve(path).getParent();

        if (parentDirectory == null) {
            throw new IllegalStateException("\"path\" might only consist of a slash!");
        }

        Path resolverProperties = parentDirectory.resolve("resolver-status.properties");

        if (!path.endsWith("/maven-metadata.xml")) {
            throw new IllegalArgumentException("This method may not be used to resolve anything but maven-metadata.xml (although it may be in various folders). Instead \"" + path + "\" was used as an input.");
        }

        List<CompletableFuture<RepositoryAttachedValue<Path>>> futures = new ArrayList<>();
        Path mvnLocalMeta = parentDirectory.resolve("maven-metadata-local.xml");

        if (Files.exists(mvnLocalMeta)) {
            futures.add(CompletableFuture.completedFuture(new RepositoryAttachedValue<>(null, mvnLocalMeta)));
        }

        ResolverMetaStatus resolverStatus = ResolverMetaStatus.tryParse(resolverProperties);

        for (MavenRepository remote : this.remoteRepositories) {
            Path localFile = parentDirectory.resolve("maven-metadata-" + remote.getRepositoryId() + ".xml");
            Long lastFetch = resolverStatus.getLastFetchTime(remote.getRepositoryId());

            if (lastFetch != null && (lastFetch + remote.getUpdateIntervall()) > System.currentTimeMillis()) {
                if (resolverStatus.hasErrored(remote.getRepositoryId())) {
                    continue;
                } else if (Files.exists(localFile)) {
                    // The cache is still valid - no need to fetch!
                    futures.add(CompletableFuture.completedFuture(new RepositoryAttachedValue<>(remote, localFile)));
                    continue;
                }
            }

            // This future downloads from the remote repository and updates the error timestamp
            // if it errors while no caches are present.
            CompletableFuture<RepositoryAttachedValue<byte[]>> fetchFuture = ConcurrencyUtil.exceptionally(
                    remote.getResource(path, executor),
                    (ex) -> {
                        if (Files.exists(localFile)) {
                            // Don't update the repository fetch timestamp here.
                            // This is beneficial for when a repository is temporarily down or the host is down too.
                            // The caches are used later on.
                            return null;
                        }
                        resolverStatus.updateEntryErrored(remote.getRepositoryId(), ex.toString(), System.currentTimeMillis());
                        return null;
                    });

            // This future writes the raw bytes fetched from the remote to disk. It then returns the path the bytes were written to.
            CompletableFuture<RepositoryAttachedValue<Path>> future = fetchFuture.thenApply((rav) -> {
                resolverStatus.updateEntrySuccess(remote.getRepositoryId(), System.currentTimeMillis());

                try {
                    Files.createDirectories(parentDirectory);
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                }

                this.write(rav.getValue(), localFile);
                return new RepositoryAttachedValue<>(rav.getRepository(), localFile);
            });

            // This future will use pre-existing caches should a download not be possible.
            // Of course if there are no caches, it will still fail exceptionally.
            future = ConcurrencyUtil.exceptionally(future, (ex) -> {
                        if (Files.exists(localFile)) {
                            return new RepositoryAttachedValue<>(remote, localFile);
                        } else {
                            return null;
                        }
                    }
            );
            futures.add(future);
        }

        // Fallback in case the file was installed directly to the local maven repository with proper maven metadata
        // This is a rarer usecase, but one usecase is for example stianloader's nightly-paperpusher where such a repository
        // needs to be managed.
        Path directMetadata = parentDirectory.resolve("maven-metadata.xml");

        if (Files.exists(directMetadata)) {
            futures.add(CompletableFuture.completedFuture(new RepositoryAttachedValue<>(null, directMetadata)));
        }

        CompletableFuture<List<RepositoryAttachedValue<Path>>> combined;

        if (futures.isEmpty()) {
            combined = JavaInterop.failedFuture(new IllegalStateException("The requested resource '" + path + "' does not exist: All registered remote repositories have been unable to download the resource within their update intervall; re-resolution will not be attempted.").fillInStackTrace());
        } else {
            combined = new StronglyMultiCompletableFuture<>(futures);
        }

        if (this.writeMetadata) {
            combined = combined.thenApply((value) -> {
                try {
                    resolverStatus.write(resolverProperties);
                } catch (Throwable ignored) { }

                return value;
            });
        }

        return combined;
    }

    @Override
    @NotNull
    public CompletableFuture<RepositoryAttachedValue<Path>> resolveStandard(@NotNull String path, @NotNull Executor executor) {
        Path localFile = this.mavenLocal.resolve(path);
        Path lastUpdateFile = this.mavenLocal.resolve(path + ".lastUpdated");
        Path remoteRepos = localFile.resolveSibling("_remote.repositories");
        Path parentDir = localFile.getParent();

        assert parentDir != null; // Not possible given that we know that 'mavenLocal' is not null.

        boolean localFilePresent = Files.exists(localFile);

        RemoteRepositoryProperties repoProps = RemoteRepositoryProperties.tryRead(remoteRepos);
        Optional<String> sourceRepo = repoProps.getSourceRepository(localFile.getFileName().toString());

        if (localFilePresent && !sourceRepo.isPresent()) {
            // Maven local
            return CompletableFuture.completedFuture(new RepositoryAttachedValue<>(null, localFile));
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
                    return CompletableFuture.completedFuture(new RepositoryAttachedValue<>(remote, localFile));
                } else if ((lastFetch + remote.getUpdateIntervall()) > System.currentTimeMillis()) {
                    // The cache is still valid - no need to fetch!
                    return CompletableFuture.completedFuture(new RepositoryAttachedValue<>(remote, localFile));
                } else {
                    candidateRepositories.clear();
                    candidateRepositories.add(remote);
                    break;
                }
            }

            if (!lastUpdated.hasErrored(remote.getPlaintextURL())) {
                if (lastFetch != null && (lastFetch + remote.getUpdateIntervall()) > System.currentTimeMillis()) {
                    return CompletableFuture.completedFuture(new RepositoryAttachedValue<>(remote, localFile));
                }
            } else if (lastFetch != null && (lastFetch + remote.getUpdateIntervall()) > System.currentTimeMillis()) {
                continue;
            }

            candidateRepositories.add(remote);
        }

        if (candidateRepositories.isEmpty()) {
            String errorMessage;

            if (localFilePresent) {
                errorMessage = "The requested resource '"
                        + path
                        + "' is present in the local maven repository, but was resolved from repository '"
                        + sourceRepo.get()
                        + "', which is not a known repository in the current resolution context. All registered remote repositories have been unable to download the resource within their update intervall.";
            } else {
                errorMessage = "The requested resource '"
                        + path
                        + "' is not present in the local maven repository. All registered remote repositories have been unable to download the resource within their update intervall.";
            }

            return JavaInterop.failedFuture(new IOException(errorMessage).fillInStackTrace());
        }

        List<CompletableFuture<RepositoryAttachedValue<byte[]>>> futures = new ArrayList<>();

        for (MavenRepository remote : candidateRepositories) {
            CompletableFuture<RepositoryAttachedValue<byte[]>> future = remote.getResource(path, executor);

            future.exceptionally((ex) -> {
                lastUpdated.updateEntryErrored(remote.getPlaintextURL(), ex.toString(), System.currentTimeMillis());
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

        CompletableFuture<RepositoryAttachedValue<byte[]>> combined;

        if (!futures.isEmpty()) {
            combined = new MultiCompletableFuture<>(futures);
        } else {
            combined = JavaInterop.failedFuture(new IOException("There are no remote repositories to fetch the file from and the file is not stored locally.").fillInStackTrace());
        }

        CompletableFuture<RepositoryAttachedValue<Path>> ret = ConcurrencyUtil.exceptionally(combined.thenApply((rav) -> {
            try {
                Files.createDirectories(parentDir);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }

            this.write(rav.getValue(), localFile);
            MavenRepository originRepository = rav.getRepository();

            if (originRepository != null) {
                repoProps.setSourceRepository(localFile.getFileName().toString(), originRepository.getRepositoryId());

                if (this.writeMetadata) {
                    repoProps.tryWrite(remoteRepos);
                }
            }

            return new RepositoryAttachedValue<>(originRepository, localFile);
        }), (ex) -> {
            if (localFilePresent) {
                String errorMessage = "The requested resource '"
                        + path
                        + "' is present in the local maven repository, but was resolved from repository '"
                        + sourceRepo.get()
                        + "', which is not a known repository in the current resolution context. All registered remote repositories have been unable to download the resource within their update intervall, or have otherwise failed to resolve the requested resource.";

                throw new UncheckedIOException((IOException) new IOException(errorMessage).initCause(ex));
            }

            return null;
        });

        if (this.writeMetadata) {
            ret.thenRun(() -> {
                try {
                    lastUpdated.write(lastUpdateFile);
                } catch (Throwable ignored) {
                }
            });
        }

        return ret;
    }

    @Override
    @NotNull
    @Contract(mutates = "this", pure = false, value = "_ -> this")
    public MavenLocalRepositoryNegotiator setWriteCacheMetadata(boolean writeMetadata) {
        this.writeMetadata = writeMetadata;
        return this;
    }

    protected void write(byte @NotNull[] data, @NotNull Path to) {
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
                } catch (IOException ignored) { }
            }
        }
    }
}
