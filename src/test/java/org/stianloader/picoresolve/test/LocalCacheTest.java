package org.stianloader.picoresolve.test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import org.junit.jupiter.api.Test;
import org.stianloader.picoresolve.GAV;
import org.stianloader.picoresolve.MavenResolver;
import org.stianloader.picoresolve.repo.MavenLocalRepositoryNegotiator;
import org.stianloader.picoresolve.repo.MavenRepository;
import org.stianloader.picoresolve.repo.RepositoryAttachedValue;
import org.stianloader.picoresolve.repo.URIMavenRepository;
import org.stianloader.picoresolve.test.util.FileDeleter;
import org.stianloader.picoresolve.test.util.TestResourceRepository;
import org.stianloader.picoresolve.version.MavenVersion;
import org.stianloader.picoresolve.version.VersionRange;

public class LocalCacheTest {

    @Test
    public void downloadArtifact() throws InterruptedException, ExecutionException, IOException {
        Path localRoot = Paths.get("testmvnlocal");
        Path gaRoot = localRoot.resolve("org/stianloader/picoresolve");
        Path gavceFile = gaRoot.resolve("1.1.2-a20260611/picoresolve-1.1.2-a20260611.jar");
        Path gavceRelativePath = localRoot.relativize(gavceFile);
        FileDeleter.deleteDir(gaRoot);

        assertDoesNotThrow(() -> {
            MavenResolver resolver = new MavenResolver(localRoot)
                    .addRepository(new URIMavenRepository("stianloader", URI.create("https://stianloader.org/maven/")));
            MavenResolver localOnlyResolver = new MavenResolver(localRoot);
            MavenResolver unrelatedResolver = new MavenResolver(localRoot).addRepository(new TestResourceRepository());

            GAV testGAV = new GAV("org.stianloader", "picoresolve", MavenVersion.parse("1.1.2-a20260611"));

            RepositoryAttachedValue<Path> pathRAV = resolver.download(testGAV, null, "jar", Runnable::run).get();
            CompletableFuture<RepositoryAttachedValue<Path>> pathRAVLocalOnly = localOnlyResolver.download(testGAV, null, "jar", Runnable::run);
            CompletableFuture<RepositoryAttachedValue<Path>> pathRAVUnrelated = unrelatedResolver.download(testGAV, null, "jar", Runnable::run);
            MavenRepository repo = pathRAV.getRepository();

            assertNotNull(repo);
            assertEquals("stianloader", repo.getRepositoryId());
            assertTrue(pathRAV.getValue().endsWith(gavceRelativePath));

            assertTrue(pathRAVLocalOnly.isCompletedExceptionally());
            assertTrue(pathRAVLocalOnly.isDone());

            assertTrue(pathRAVUnrelated.isCompletedExceptionally());
            assertTrue(pathRAVUnrelated.isDone());

            try {
                pathRAVLocalOnly.get();
                throw new AssertionError("Unreachable code");
            } catch (ExecutionException ex) {
                Throwable cause = ex.getCause();
                assertNotNull(cause);
                String message = cause.getMessage();
                assertNotNull(message);
                assertTrue(message.contains("repository 'stianloader', which is not a known repository in the current resolution context"));
            }

            try {
                pathRAVUnrelated.get();
                throw new AssertionError("Unreachable code");
            } catch (ExecutionException ex) {
                Throwable cause = ex.getCause();
                assertNotNull(cause);
                String message = cause.getMessage();
                assertNotNull(message);
                assertTrue(message.contains("repository 'stianloader', which is not a known repository in the current resolution context"));
            }

            // remove the source remote metadata
            Files.move(gavceFile.resolveSibling("_remote.repositories"), gavceFile.resolveSibling("_remote.repositories.ignored"));

            RepositoryAttachedValue<Path> pathRAV2 = resolver.download(testGAV, null, "jar", Runnable::run).get();
            RepositoryAttachedValue<Path> pathRAV2LocalOnly = localOnlyResolver.download(testGAV, null, "jar", Runnable::run).get();
            RepositoryAttachedValue<Path> pathRAV2Unrelated = localOnlyResolver.download(testGAV, null, "jar", Runnable::run).get();

            assertNull(pathRAV2.getRepository()); // Without the _remote.repositories file, contents should be assumed to come from the local repository
            assertTrue(pathRAV2.getValue().endsWith(gavceRelativePath));

            assertNull(pathRAV2LocalOnly.getRepository());
            assertTrue(pathRAV2LocalOnly.getValue().endsWith(gavceRelativePath));

            assertNull(pathRAV2Unrelated.getRepository());
            assertTrue(pathRAV2Unrelated.getValue().endsWith(gavceRelativePath));
        });
    }

    @Test
    public void noWriteMetadata() throws InterruptedException, ExecutionException, IOException {
        Path localRoot = Paths.get("testmvnlocal");
        Path gaRoot = localRoot.resolve("org/stianloader/nometatest");

        FileDeleter.deleteDir(gaRoot);

        MavenResolver resolver = new MavenResolver(new MavenLocalRepositoryNegotiator(localRoot).setWriteCacheMetadata(false));

        CompletableFuture<?> cfUseMeta = resolver.download("org.stianloader", "nometatest", VersionRange.RELEASE, null, "jar", Runnable::run);

        assertTrue(cfUseMeta.isDone());
        assertTrue(cfUseMeta.isCompletedExceptionally());
        assertFalse(Files.exists(gaRoot));

        try {
            cfUseMeta.get();
            throw new AssertionError("Unreachable code");
        } catch (ExecutionException ce) {
            Throwable cause = ce.getCause();
            assertNotNull(cause);
            // Test that error reporting is done properly
            assertTrue(cause.getMessage().contains("'org/stianloader/nometatest/maven-metadata.xml' does not exist: All registered remote repositories have been unable to download the resource within their update intervall"));
        }

        CompletableFuture<?> cfUseDirect = resolver.download(new GAV("org.stianloader", "nometatest", MavenVersion.parse("1.0.0")), null, "jar", Runnable::run);

        assertTrue(cfUseDirect.isDone());
        assertTrue(cfUseDirect.isCompletedExceptionally());
        assertFalse(Files.exists(gaRoot));

        try {
            cfUseDirect.get();
            throw new AssertionError("Unreachable code");
        } catch (ExecutionException ce) {
            Throwable cause = ce.getCause();
            assertNotNull(cause);
            // Test that error reporting is done properly
            assertTrue(cause.getMessage().contains("'org/stianloader/nometatest/1.0.0/nometatest-1.0.0.jar' is not present in the local maven repository"));
        }
    }
}
