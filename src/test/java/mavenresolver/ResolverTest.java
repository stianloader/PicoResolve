package mavenresolver;

import java.net.URI;
import java.nio.file.Paths;
import java.util.concurrent.Executor;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.Semaphore;

import org.junit.jupiter.api.Test;

import de.geolykt.mavenresolver.MavenResolver;
import de.geolykt.mavenresolver.URIMavenRepository;
import de.geolykt.mavenresolver.misc.ObjectSink;
import de.geolykt.mavenresolver.version.VersionCatalogue;

public class ResolverTest {

    public void printVersions(String group, String artifact) {
        MavenResolver resolver = new MavenResolver().addRepository(new URIMavenRepository(Paths.get("testcache"), URI.create("https://repo1.maven.org/maven2/")));
        Executor exec = ForkJoinPool.commonPool();
        Semaphore lock = new Semaphore(1);
        lock.acquireUninterruptibly();
        resolver.getVersions(group, artifact, exec, new ObjectSink<VersionCatalogue>() {

            @Override
            public void onError(Throwable error) {
                error.printStackTrace();
                lock.release();
            }

            @Override
            public void onComplete() {
                lock.release();
            }

            @Override
            public void nextItem(VersionCatalogue item) {
                System.out.println("Last update: " + item.lastUpdated);
                System.out.println("Versions: " + item.releaseVersions);
                System.out.println("Newest: " + item.latestVersion);
                System.out.println("Release: " + item.releaseVersion);
            }
        });
        lock.acquireUninterruptibly();
    }

    @Test
    public void printVersions() {
        printVersions("org.opencsv", "opencsv");
        printVersions("org.caffetranslator", "caffe-translator");
        printVersions("org.ancoron.glassfish.lib", "lib-parent");
        printVersions("org.alfasoftware", "soapstone");
        printVersions("org.alfasoftware", "astra");
    }
}
