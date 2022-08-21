package de.geolykt.mavenresolver;

import java.net.URI;
import java.util.concurrent.Executor;

import de.geolykt.mavenresolver.misc.ObjectSink;
import de.geolykt.mavenresolver.misc.SinkMultiplexer;

public class URIMavenRepository implements MavenRepository {

    private final class BaseMavenResource implements MavenResource {

        private final byte[] bytes;

        public BaseMavenResource(byte[] bytes) {
            this.bytes = bytes;
        }

        @Override
        public byte[] getBytes() {
            return bytes;
        }
    }

    private final URI base;

    public URIMavenRepository(URI base) {
        this.base = base;
    }

    @Override
    public void getResource(String path, Executor executor, ObjectSink<MavenResource> sink) {
        executor.execute(() -> {
            BaseMavenResource resource;
            try {
                URI resolved = base.resolve(path);
                System.out.println("Downloading " + resolved);
                resource = new BaseMavenResource(resolved.toURL().openStream().readAllBytes());
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
