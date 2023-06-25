package de.geolykt.picoresolve;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

import de.geolykt.picoresolve.DependencyLayer.DependencyEdge;
import de.geolykt.picoresolve.DependencyLayer.DependencyLayerElement;
import de.geolykt.picoresolve.exclusion.Exclusion;
import de.geolykt.picoresolve.exclusion.ExclusionContainer;
import de.geolykt.picoresolve.version.VersionRange;

class DependencyContainerNode {

    final List<SubdependencyNode> dependencies = new ArrayList<>();

    final GAV gav;

    DependencyContainerNode(GAV gav) {
        this.gav = gav;
    }

    class SubdependencyNode {

        final String group;
        final String artifact;
        final String classifier;
        final String type;
        final ExclusionContainer<Exclusion> exclusions;
        final Scope scope;
        final VersionRange version;

        SubdependencyNode(String group, String artifact, String classifier, String type, VersionRange version, Scope scope, ExclusionContainer<Exclusion> exclusions) {
            if (type == null) {
                type = "jar";
            }
            if (exclusions == null) {
                exclusions = ExclusionContainer.empty();
            }
            this.group = group;
            this.artifact = artifact;
            this.classifier = classifier;
            this.type = type;
            this.scope = scope;
            this.version = version;
            this.exclusions = exclusions;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }
            if (obj instanceof SubdependencyNode other) {
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

    void createDependency(String group, String artifactId, String classifier, String type, VersionRange version, Scope scope, ExclusionContainer<Exclusion> exclusions) {
        if (type == null) {
            type = "jar";
        }

        SubdependencyNode dep = this.selectDependency(group, artifactId, classifier, type);
        if (dep != null) {
            throw new IllegalStateException("Dependency already exists");
        }
        dep = new SubdependencyNode(group, artifactId, classifier, type, version, scope, exclusions);
        this.dependencies.add(dep);
    }

    SubdependencyNode selectDependency(String group, String artifactId, String classifier, String type) {
        if (type == null) {
            type = "jar";
        }
        for (SubdependencyNode node : this.dependencies) {
            if (node.group.equals(group)
                    && node.artifact.equals(artifactId)
                    && Objects.equals(node.classifier, classifier)
                    && node.type.equals(type)) {
                return node;
            }
        }
        return null;
    }

    public DependencyLayerElement toLayerElement(String classifier, String type, ExclusionContainer<?> inheritedExclusions) {
        List<DependencyEdge> edges = new ArrayList<>();
        for (SubdependencyNode node : this.dependencies) {
            if (inheritedExclusions.isExcluding(node.group, node.artifact)) {
                continue;
            }
            edges.add(new DependencyEdge(new VersionlessDependency(node.group, node.artifact, node.classifier, node.type), node.version, node.scope, node.exclusions));
        }
        return new DependencyLayerElement(this.gav, classifier, type, inheritedExclusions, Collections.unmodifiableList(edges));
    }
}
