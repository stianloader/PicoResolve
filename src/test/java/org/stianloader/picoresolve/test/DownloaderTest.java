package org.stianloader.picoresolve.test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

import java.net.URI;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.junit.jupiter.api.Test;
import org.stianloader.picoresolve.GAV;
import org.stianloader.picoresolve.MavenResolver;
import org.stianloader.picoresolve.repo.MavenRepository;
import org.stianloader.picoresolve.repo.RepositoryAttachedValue;
import org.stianloader.picoresolve.repo.URIMavenRepository;
import org.stianloader.picoresolve.version.MavenVersion;

public class DownloaderTest {

    // TODO Figure out why this test doesn't run
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
}
