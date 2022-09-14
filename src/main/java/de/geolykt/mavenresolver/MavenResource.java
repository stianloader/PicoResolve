package de.geolykt.mavenresolver;

import java.nio.file.Path;

public interface MavenResource {

    /**
     * Obtains the path where the maven resource is stored locally.
     * This path can be temporary if needed, but should generally point to a cache if applicable.
     *
     * @return The path where the resource is located.
     */
    Path getPath();

    /**
     * Obtains the {@link MavenRepository} in which the maven resource was located in.
     * Note that caches can skew the location of repositories.
     *
     * @return The repository storing the resource.
     */
    MavenRepository getSourceRepository();
}
