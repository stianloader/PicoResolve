package org.stianloader.picoresolve;

import java.util.Objects;

import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.stianloader.picoresolve.version.MavenVersion;
import org.stianloader.picoresolve.version.VersionRange;

/**
 * A GAV stores the group, artifact and version of a resource. A GAV is usually accompanied by
 * a classifier and an file extension/type in order for a complete resource lookup to work,
 * but in most cases a GAV is adequate to resolve things like the "default" jar file of an artifact
 * or to get the POM or to list the snapshot versions.
 *
 * <p>However because of the decision to separate out {@link MavenVersion} and {@link VersionRange} in
 * two hierarchically completely different classes, this GAV object is unsuited to define dependencies as dependencies
 * can be defined through version ranges and not a singular, very direct version string.
 *
 * <p>Most resource lookups will use {@link MavenVersion#getOriginText()} for the version part of the lookup,
 * as the actual (corrected and parsed) version string may differ to those actually needed. This means
 * that things such as case-sensitivity is important when performing the inevitable {@link MavenVersion#parse(String)}
 * call before invoking the constructor of this class.
 */
public final class GAV {
    @NotNull
    private final String artifact;
    @NotNull
    private final String group;
    @NotNull
    private final MavenVersion version;

    public GAV(@NotNull String group, @NotNull String artifact, @NotNull MavenVersion version) {
        this.group = group;
        this.artifact = artifact;
        this.version = version;
    }

    @NotNull
    @Contract(pure = true)
    public String artifact() {
        return this.artifact;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj instanceof GAV) {
            GAV other = (GAV) obj;
            return other.group.equals(this.group)
                    && other.artifact.equals(this.artifact)
                    && other.version.equals(this.version);
        }
        return false;
    }

    @NotNull
    @Contract(pure = true)
    public String group() {
        return this.group;
    }

    @Override
    public int hashCode() {
        return Objects.hash(this.group, this.artifact, this.version);
    }

    @NotNull
    @Contract(pure = true)
    public MavenVersion version() {
        return this.version;
    }

    @Override
    public String toString() {
        return this.group + ":" + this.artifact + ":" + this.version.getOriginText();
    }
}
