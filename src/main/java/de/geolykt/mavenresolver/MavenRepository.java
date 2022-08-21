package de.geolykt.mavenresolver;

import java.util.concurrent.Executor;

import de.geolykt.mavenresolver.misc.ObjectSink;

public interface MavenRepository {

    void getResource(String path, Executor executor, ObjectSink<MavenResource> sink);
}
