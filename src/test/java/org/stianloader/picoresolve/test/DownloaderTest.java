package org.stianloader.picoresolve.test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URI;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.ExecutionException;

import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.stianloader.picoresolve.DependencyLayer;
import org.stianloader.picoresolve.GAV;
import org.stianloader.picoresolve.MavenResolver;
import org.stianloader.picoresolve.repo.MavenRepository;
import org.stianloader.picoresolve.repo.RepositoryAttachedValue;
import org.stianloader.picoresolve.repo.URIMavenRepository;
import org.stianloader.picoresolve.version.MavenVersion;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class DownloaderTest {
    @Test
    @Order(value = 1)
    public void downloadSparkSnapshot() throws InterruptedException, ExecutionException {
        assertDoesNotThrow(() -> {
            MavenResolver resolver = new MavenResolver(Paths.get("testmvnlocal"))
                    .addRepository(new URIMavenRepository("stianloader-central-mirror", URI.create("https://stianloader.org/central-mirror/")));

            // me.lucko:spark-common:1.10.142-SNAPSHOT
            GAV sparkGAV = new GAV("me.lucko", "spark-common", MavenVersion.parse("1.10.142-SNAPSHOT"));

            RepositoryAttachedValue<Path> pathRAV = resolver.download(sparkGAV, null, "jar", Runnable::run).get();
            MavenRepository repo = pathRAV.getRepository();

            assertNotNull(repo);
            assertEquals("stianloader-central-mirror", repo.getRepositoryId());
            assertTrue(pathRAV.getValue().endsWith("me/lucko/spark-common/1.10.142-SNAPSHOT/spark-common-1.10.142-20250721.212358-1.jar"));

            DependencyLayer layer = DependencyLayer.createLayerFor(new GAV("", "", MavenVersion.parse("")), sparkGAV);
            resolver.resolveAllChildren(layer, Runnable::run).join();
        });
    }

    @Test
    @Order(value = 2)
    public void downloadSparkSnapshotMultirepo() throws InterruptedException, ExecutionException {
        assertDoesNotThrow(() -> {
            // Due to multiple repositories containing the queries snapshot artifacts - some of which might not be up to date -
            // the snapshot version catalogues must get merged. This is a distinctively different process from just downloading
            // from a single repository.

            MavenResolver resolver = new MavenResolver(Paths.get("testmvnlocal"))
                    .addRepository(new URIMavenRepository("stianloader-new-mirror", URI.create("https://stianloader.org/maven2/all/")))
                    .addRepository(new URIMavenRepository("stianloader-central-mirror", URI.create("https://stianloader.org/central-mirror/")));

            // me.lucko:spark-common:1.10.142-SNAPSHOT
            GAV sparkGAV = new GAV("me.lucko", "spark-common", MavenVersion.parse("1.10.142-SNAPSHOT"));

            RepositoryAttachedValue<Path> pathRAV = resolver.download(sparkGAV, null, "jar", Runnable::run).get();
            MavenRepository repo = pathRAV.getRepository();

            assertNotNull(repo);
            // because the artifact has already been downloaded from one repository, there is little need to download it from another.
            // We test for this behaviour here, though honestly this is not a strict requirement and more of a performance-saving measure.
            // Either way, it should never say it is from "stianloader-new-mirror" when in fact it is not.
            assertEquals("stianloader-central-mirror", repo.getRepositoryId());
            assertTrue(pathRAV.getValue().endsWith("me/lucko/spark-common/1.10.142-SNAPSHOT/spark-common-1.10.142-20250721.212358-1.jar"));

            DependencyLayer layer = DependencyLayer.createLayerFor(new GAV("", "", MavenVersion.parse("")), sparkGAV);
            resolver.resolveAllChildren(layer, Runnable::run).join();
        });
    }
}
