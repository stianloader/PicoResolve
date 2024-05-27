package org.stianloader.picoresolve;

import java.util.Objects;

import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

final class VersionlessDependency {

    @NotNull
    private final String artifact;

    @Nullable
    private final String classifier;

    @NotNull
    private final String group;

    @Nullable
    private final String type;

    public VersionlessDependency(@NotNull String group, @NotNull String artifact, @Nullable String classifier, @Nullable String type) {
        this.group = group;
        this.artifact = artifact;
        this.classifier = classifier;
        this.type = type;
    }

    @NotNull
    @Contract(pure = true)
    public String artifact() {
        return this.artifact;
    }

    @Nullable
    @Contract(pure = true)
    public String classifier() {
        return this.classifier;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj instanceof VersionlessDependency) {
            VersionlessDependency other = (VersionlessDependency) obj;
            return other.artifact.equals(this.artifact)
                    && other.group.equals(this.group)
                    && Objects.equals(other.classifier, this.classifier)
                    && Objects.equals(other.type, this.type);
        }
        return false;
    }

    @NotNull
    @Contract(pure = true)
    String getType(@NotNull String defaultValue) {
        String t = this.type;
        if (t == null) {
            return defaultValue;
        }
        return t;
    }

    @NotNull
    @Contract(pure = true)
    public String group() {
        return this.group;
    }

    @Override
    public int hashCode() {
        return Objects.hash(this.group, this.artifact, this.classifier, this.type);
    }

    @Nullable
    @Contract(pure = true)
    public String type() {
        return this.type;
    }
}
