package mavenresolver;

import java.net.URI;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.Semaphore;

import org.junit.jupiter.api.Test;

import de.geolykt.mavenresolver.GAV;
import de.geolykt.mavenresolver.MavenResolver;
import de.geolykt.mavenresolver.internal.ObjectSink;
import de.geolykt.mavenresolver.repo.RepositoryAttachedValue;
import de.geolykt.mavenresolver.repo.URIMavenRepository;
import de.geolykt.mavenresolver.version.MavenVersion;

public class DownloaderTest {

    @Test
    public void doTest() {
        MavenResolver resolver = new MavenResolver(Paths.get("testmvnlocal"))
                .addRepository(new URIMavenRepository("central", URI.create("https://repo1.maven.org/maven2/")))
                .addRepository(new URIMavenRepository("paper", URI.create("https://papermc.io/repo/repository/maven-public/")));

        Semaphore lock = new Semaphore(1);
        lock.acquireUninterruptibly();
        GAV paperGAV = new GAV("io.papermc.paper", "paper-api", MavenVersion.parse("1.18.1-R0.1-SNAPSHOT"));
        resolver.download(paperGAV, null, "jar", ForkJoinPool.commonPool(), new ObjectSink<RepositoryAttachedValue<Path>>() {
            @Override
            public void onError(Throwable error) {
                error.printStackTrace();
                lock.release();
            }

            @Override
            public void onComplete() {
                System.out.println("Download ended");
                lock.release();
            }

            @Override
            public void nextItem(RepositoryAttachedValue<Path> item) {
                System.out.println("Download success: " + item.getValue());
            }
        });
        lock.acquireUninterruptibly();
    }
}
