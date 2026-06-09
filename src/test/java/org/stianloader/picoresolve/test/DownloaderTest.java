package org.stianloader.picoresolve.test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.net.URI;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.ExecutionException;

import org.junit.jupiter.api.Test;
import org.stianloader.picoresolve.DependencyLayer;
import org.stianloader.picoresolve.GAV;
import org.stianloader.picoresolve.MavenResolver;
import org.stianloader.picoresolve.repo.MavenRepository;
import org.stianloader.picoresolve.repo.RepositoryAttachedValue;
import org.stianloader.picoresolve.repo.URIMavenRepository;
import org.stianloader.picoresolve.version.MavenVersion;

public class DownloaderTest {
    @Test
    public void doTest() {
        assertDoesNotThrow(() -> {
            try {
                MavenResolver resolver = new MavenResolver(Paths.get("testmvnlocal"))
                        .addRepository(new URIMavenRepository("central", URI.create("https://repo1.maven.org/maven2/")))
                        .addRepository(new URIMavenRepository("paper", URI.create("https://papermc.io/repo/repository/maven-public/")));

                GAV paperGAV = new GAV("io.papermc.paper", "paper-api", MavenVersion.parse("1.18.1-R0.1-SNAPSHOT"));

                RepositoryAttachedValue<Path> pathRAV = resolver.download(paperGAV, null, "jar", Runnable::run).get();
                MavenRepository repo = pathRAV.getRepository();
                System.out.println("Download success: " + (repo == null ? "unknown" : repo.getRepositoryId()) + ":" + pathRAV.getValue());
            } catch (Throwable t) {
                t.printStackTrace();
                throw t;
            }
        });
    }

    @Test
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

            DependencyLayer layer = DependencyLayer.createLayerFor(new GAV("", "", MavenVersion.parse("")), sparkGAV);
            resolver.resolveAllChildren(layer, Runnable::run).join();
        });
    }
}
