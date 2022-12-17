package de.geolykt.mavenresolver.repo;

import java.util.Objects;

/**
 * A {@link RepositoryAttachedValue} is - as it's name implies - a value
 * that is attached to a repository. As of now it is used the file downloading
 * and repository negotiation process. The main purpose for this class is to be
 * able to trace the source repositories of artifacts.
 *
 * @param <V> The type of the value
 */
public class RepositoryAttachedValue<V> {

    private final V value;
    private final MavenRepository repository;

    public RepositoryAttachedValue(V value, MavenRepository repository) {
        this.value = Objects.requireNonNull(value, "value may not be null.");
        this.repository = repository;
    }

    /**
     * Obtains the value stored by this object.
     * May not return null as that is not sensical.
     *
     * @return The attached value
     */
    public V getValue() {
        return this.value;
    }

    /**
     * Obtains the {@link MavenRepository} which is linked to the value.
     * In practice this is the repository where a file originates from,
     * may be null to indicate that this comes from MavenLocal - possibly
     * installed by user interaction. Do note that most artifacts cached
     * in MavenLocal should not return null but instead the repository where
     * they were originally coming from. Furthermore this value should not
     * return null if this object was created by any instance of {@link MavenRepository}.
     *
     * @return The repository.
     */
    public MavenRepository getRepository() {
        return this.repository;
    }
}
