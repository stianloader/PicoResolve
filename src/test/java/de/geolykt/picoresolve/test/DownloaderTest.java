package de.geolykt.picoresolve.test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

import java.net.URI;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.junit.jupiter.api.Test;

import de.geolykt.picoresolve.GAV;
import de.geolykt.picoresolve.MavenResolver;
import de.geolykt.picoresolve.repo.MavenRepository;
import de.geolykt.picoresolve.repo.RepositoryAttachedValue;
import de.geolykt.picoresolve.repo.URIMavenRepository;
import de.geolykt.picoresolve.version.MavenVersion;

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
