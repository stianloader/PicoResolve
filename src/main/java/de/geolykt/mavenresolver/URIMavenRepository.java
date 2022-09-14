package de.geolykt.mavenresolver;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.Executor;

import de.geolykt.mavenresolver.misc.ObjectSink;
import de.geolykt.mavenresolver.misc.SinkMultiplexer;

public class URIMavenRepository implements MavenRepository {

    private final class BaseMavenResource implements MavenResource {

        private final Path path;

        public BaseMavenResource(final Path path) {
            this.path = path;
        }

        @Override
        public Path getPath() {
            return path;
        }

        @Override
        public MavenRepository getSourceRepository() {
            return URIMavenRepository.this;
        }
    }

    private final URI base;
    private final Path cacheFolder;

    public URIMavenRepository(Path cacheFolder, URI base) {
        this.base = base;
        this.cacheFolder = cacheFolder;
        if (Files.notExists(cacheFolder)) {
            try {
                Files.createDirectories(cacheFolder);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    @Override
    public void getResource(String path, Executor executor, ObjectSink<MavenResource> sink) {
        // TODO Checksums
        executor.execute(() -> {
            BaseMavenResource resource;
            try {
                URI resolved = base.resolve(path);
                System.out.println("Downloading " + resolved);
                String pstring = path;
                while (pstring.codePointAt(0) == '/') {
                    pstring = pstring.substring(1);
                }
                Path cachePath = cacheFolder.resolve(pstring);
                if (!Files.exists(cachePath)) {
                    Files.createDirectories(cachePath.getParent());
                    Files.copy(resolved.toURL().openStream(), cachePath);
                }
                resource = new BaseMavenResource(cachePath);
            } catch (Exception e) {
                sink.onError(e);
                return;
            }
            try {
                sink.nextItem(resource);
                sink.onComplete();
            } catch (Exception e) {
                if (sink instanceof SinkMultiplexer) {
                    e.printStackTrace();
                }
                sink.onError(e);
            }
        });
    }
}
