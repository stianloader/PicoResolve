package org.stianloader.picoresolve.repo;

import java.util.Objects;

import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * A {@link RepositoryAttachedValue} is - as it's name implies - a value
 * that is attached to a repository. As of now it is used the file downloading
 * and repository negotiation process. The main purpose for this class is to be
 * able to trace the source repositories of artifacts.
 *
 * <p>Beware that it is permitted for the value to be attached to a null repository.
 * In this case, the value is considered to be attached to no particular repository
 * and may have directly been installed to the local maven repository.
 * However, {@link RepositoryNegotiatior} implementations should store adequate
 * metadata to be able to "track" from which repository an artifact came from even
 * across executions if the artifact was downloaded from a remote repository.
 *
 * @param <V> The type of the value
 */
public class RepositoryAttachedValue<V> {
    @NotNull
    private final V value;
    @Nullable
    private final MavenRepository repository;

    public RepositoryAttachedValue(@Nullable MavenRepository repository, @NotNull V value) {
        this.repository = repository;
        this.value = Objects.requireNonNull(value, "value may not be null.");
    }

    /**
     * Obtains the value stored by this object.
     * May not return null as that is not sensical.
     *
     * @return The attached value
     */
    @NotNull
    @Contract(pure = true)
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
     * <p>It is also permitted to use null repositories if the repository
     * the value originates from is known, but not registered to the negotiator
     * (or whichever other object emits this {@link RepositoryAttachedValue}).
     * This for example can by reproduced by altering the {@link MavenRepository#getRepositoryId() ID}
     * of a repository across executions, rendering repository tracking metadata
     * insufficient. Note: implementations are allowed to fall back to using the
     * {@link MavenRepository#getPlaintextURL() URL} of the repository instead.
     * That being said, solely relying on the ID is sufficient for most uses.
     *
     * @return The repository.
     */
    @Nullable
    @Contract(pure = true)
    public MavenRepository getRepository() {
        return this.repository;
    }
}
