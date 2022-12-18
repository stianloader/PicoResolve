package mavenresolver;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

import java.net.URI;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.junit.jupiter.api.Test;

import de.geolykt.mavenresolver.GAV;
import de.geolykt.mavenresolver.MavenResolver;
import de.geolykt.mavenresolver.repo.RepositoryAttachedValue;
import de.geolykt.mavenresolver.repo.URIMavenRepository;
import de.geolykt.mavenresolver.version.MavenVersion;

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
                System.out.println("Download success: " + pathRAV.getRepository().getRepositoryId() + ":" + pathRAV.getValue());
            } catch (Throwable t) {
                t.printStackTrace();
                throw t;
            }
        });
    }
}
