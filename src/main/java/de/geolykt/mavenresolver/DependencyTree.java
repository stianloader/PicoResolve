package de.geolykt.mavenresolver;

import java.util.Collections;
import java.util.List;
import java.util.Objects;

public class DependencyTree {

    public final GAV gav;
    public final String classifier;
    public final String type;
    public final List<DependencyTree> dependencies;
    public boolean enabled = true;

    public DependencyTree(GAV gav, String classifier, String type, List<DependencyTree> dependencies) {
        this.gav = gav;
        this.classifier = classifier;
        this.type = type;
        this.dependencies = Collections.unmodifiableList(dependencies);
    }

    @Override
    public boolean equals(Object obj) {
        if (obj instanceof DependencyTree other) {
            return this.gav.equals(other.gav)
                    && Objects.equals(this.classifier, other.classifier)
                    && Objects.equals(this.type, other.type);
        }
        return false;
    }

    @Override
    public int hashCode() {
        return Objects.hash(this.gav, this.classifier, this.type);
    }
}
