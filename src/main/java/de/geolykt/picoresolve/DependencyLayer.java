package de.geolykt.picoresolve;

import java.util.List;

import de.geolykt.picoresolve.exclusion.Exclusion;
import de.geolykt.picoresolve.exclusion.ExclusionContainer;
import de.geolykt.picoresolve.version.VersionRange;

public class DependencyLayer {
    public final DependencyLayer parent;
    public final List<DependencyLayerElement> elements;
    private DependencyLayer child;

    public DependencyLayer(DependencyLayer parent, List<DependencyLayerElement> elements) {
        this.parent = parent;
        this.elements = elements;
        for (DependencyLayerElement element : elements) {
            if (element.layer != null) {
                throw new IllegalStateException("The dependency element already has a dependency layer associated.");
            }
            element.layer = this;
        }
        if (parent != null) {
            if (parent.child != null) {
                throw new IllegalStateException("Parent already has a child dependency layer");
            }
            parent.child = this;
        }
    }

    public DependencyLayer getChild() {
        return this.child;
    }

    public static class DependencyLayerElement {
        private DependencyLayer layer;
        public final GAV gav;
        public final String classifier;
        public final String type;
        public final ExclusionContainer<?> parentExclusions;
        public final List<DependencyEdge> outgoingEdges;

        public DependencyLayerElement(GAV gav, String classifier, String type, ExclusionContainer<?> parentExclusions, List<DependencyEdge> outgoingEdges) {
            this.gav = gav;
            this.classifier = classifier;
            this.type = type == null ? "jar" : type;
            this.parentExclusions = parentExclusions;
            this.outgoingEdges = outgoingEdges;
            for (DependencyEdge edge : outgoingEdges) {
                if (edge.declarer != null) {
                    throw new IllegalStateException("The dependency edge already has a dependency layer element associated.");
                }
                edge.declarer = this;
            }
        }

        public DependencyLayer getLayer() {
            if (this.layer == null) {
                throw new IllegalStateException("This layer is not yet associated with a dependency layer.");
            }
            return this.layer;
        }

        @Override
        public String toString() {
            return "DependencyLayerElement[gav=" + gav + "]";
        }
    }

    public static class DependencyEdge {
        private DependencyLayerElement declarer;
        public final String group;
        public final String artifact;
        public final String classifier;
        public final String type;
        public final VersionRange requestedVersion;
        public final Scope scope;
        public final ExclusionContainer<Exclusion> edgeExclusion;
        private DependencyLayerElement resolved;

        public DependencyEdge(String group, String artifact, String classifier, String type, VersionRange requestedVersion, Scope scope, ExclusionContainer<Exclusion> edgeExclusion) {
            this.group = group;
            this.artifact = artifact;
            this.classifier = classifier;
            this.type = type;
            this.requestedVersion = requestedVersion;
            this.scope = scope;
            this.edgeExclusion = edgeExclusion;
        }

        DependencyEdge(VersionlessDependency coordinate, VersionRange requestedVersion, Scope scope, ExclusionContainer<Exclusion> edgeExclusion) {
            this(coordinate.group(), coordinate.artifact(), coordinate.classifier(), coordinate.type() == null ? "jar" : coordinate.type(), requestedVersion, scope, edgeExclusion);
        }

        public boolean isResolved() {
            return this.resolved != null;
        }

        public DependencyLayerElement getResolved() {
            if (this.resolved == null) {
                throw new IllegalStateException("Edge has not yet been resolved [" + this.group + ":" + this.artifact + "]! It is declared by " + (this.declarer != null ? this.getDeclarer().gav : "null"));
            }
            return this.resolved;
        }

        void resolve(DependencyLayerElement element) {
            if (this.resolved != null) {
                throw new IllegalStateException("Edge is already resolved!");
            }
            this.resolved = element;
        }

        public DependencyLayerElement getDeclarer() {
            if (this.declarer == null) {
                throw new IllegalStateException("This edge is not yet associated with a dependency layer element.");
            }
            return this.declarer;
        }
    }

    // !!! BELOW IS OLDER (most likely nonsensical) CODE. REMOVE IT IN PROD AT LATEST !!!

    /*
    public final DependencyLayer parent;
    final Map<VersionlessDependency, MavenVersion> declaredVersions = new ConcurrentHashMap<>();
    final Map<VersionlessDependency, DependencyAggregation> dependencyReferrals = new ConcurrentHashMap<>();

    public DependencyLayer(DependencyLayer parent) {
        this.parent = parent;
    }

    boolean isDefined(VersionlessDependency dependency) {
        return this.declaredVersions.containsKey(dependency) || (this.parent != null && this.parent.isDefined(dependency));
    }

    class DependencyAggregation {
        VersionRange effectiveRange = VersionRange.FREE_RANGE;
        final ExclusionContainer<ExclusionContainer<?>> effectiveExclusions;

        public DependencyAggregation() {
            this.effectiveExclusions = new ExclusionContainer<>(ExclusionMode.ALL);
        }

        public void aggregateRequests(VersionRange requestedRange, Collection<ExclusionContainer<?>> requestedExclusions) {
            this.effectiveRange = this.effectiveRange.intersect(requestedRange);
            for (ExclusionContainer<?> container : requestedExclusions) {
                this.effectiveExclusions.addChild(container);
            }
        }
    }*/
}
