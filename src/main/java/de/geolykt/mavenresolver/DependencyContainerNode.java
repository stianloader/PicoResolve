package de.geolykt.mavenresolver;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import de.geolykt.mavenresolver.version.VersionRange;

class DependencyContainerNode {

    final List<DependencyNode> dependencies = new ArrayList<>();

    final GAV gav;

    DependencyContainerNode(GAV gav) {
        this.gav = gav;
    }

    class DependencyNode {

        final String group;
        final String artifact;
        final String classifier;
        final String type;
        final Set<Exclusion> exclusions;
        final EnumSet<Scope> scopes = EnumSet.noneOf(Scope.class);
        VersionRange version;

        DependencyNode(String group, String artifact, String classifier, String type, Set<Exclusion> exclusions) {
            if (type == null) {
                type = "jar";
            }
            this.group = group;
            this.artifact = artifact;
            this.classifier = classifier;
            this.type = type;
            this.exclusions = new HashSet<>(exclusions);
        }

        void addExclusion(Exclusion exclusion) {
            if (exclusion.artifact() == null || exclusion.group() == null) {
                return;
            }
            this.exclusions.add(exclusion);
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }
            if (obj instanceof DependencyNode other) {
                return Objects.equals(other.group, this.group)
                        && Objects.equals(other.artifact, this.artifact)
                        && Objects.equals(other.classifier, this.classifier)
                        && Objects.equals(other.type, this.type);
            }
            return false;
        }

        @Override
        public int hashCode() {
            return Objects.hash(this.group, this.artifact, this.classifier, this.type);
        }
    }

    DependencyNode getOrCreateDependency(String group, String artifactId, String classifier, String type) {
        DependencyNode dep = selectDependency(group, artifactId, classifier, type);
        if (dep != null) {
            return dep;
        }
        dep = new DependencyNode(group, artifactId, classifier, type, Collections.emptySet());
        dependencies.add(dep);
        return dep;
    }

    DependencyNode selectDependency(String group, String artifactId, String classifier, String type) {
        if (type == null) {
            type = "jar";
        }
        for (DependencyNode node : dependencies) {
            if (node.group.equals(group)
                    && node.artifact.equals(artifactId)
                    && Objects.equals(node.classifier, classifier)
                    && node.type.equals(type)) {
                return node;
            }
        }
        return null;
    }
}
