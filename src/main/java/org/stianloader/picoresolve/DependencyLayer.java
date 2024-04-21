package org.stianloader.picoresolve;

import java.util.List;

import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.stianloader.picoresolve.exclusion.Exclusion;
import org.stianloader.picoresolve.exclusion.ExclusionContainer;
import org.stianloader.picoresolve.version.VersionRange;

public class DependencyLayer {
    @Nullable
    public final DependencyLayer parent;
    @NotNull
    public final List<DependencyLayerElement> elements;
    @Nullable
    private DependencyLayer child;

    public DependencyLayer(@Nullable DependencyLayer parent, @NotNull List<DependencyLayerElement> elements) {
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

    @Contract(pure = true)
    @Nullable
    public DependencyLayer getChild() {
        return this.child;
    }

    public static class DependencyLayerElement {
        @Nullable
        private DependencyLayer layer;
        @NotNull
        public final GAV gav;
        @Nullable
        public final String classifier;
        @NotNull
        public final String type;
        @NotNull
        public final ExclusionContainer<?> parentExclusions;
        @NotNull
        public final List<DependencyEdge> outgoingEdges;

        public DependencyLayerElement(@NotNull GAV gav, @Nullable String classifier, @Nullable String type, @NotNull ExclusionContainer<?> parentExclusions, @NotNull List<DependencyEdge> outgoingEdges) {
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

        @NotNull
        public DependencyLayer getLayer() {
            DependencyLayer layer = this.layer;
            if (layer == null) {
                throw new IllegalStateException("This layer is not yet associated with a dependency layer.");
            }
            return layer;
        }

        @Override
        @NotNull
        public String toString() {
            return "DependencyLayerElement[gav=" + this.gav + "]";
        }
    }

    public static class DependencyEdge {
        @Nullable
        private DependencyLayerElement declarer;
        @NotNull
        public final String group;
        @NotNull
        public final String artifact;
        @Nullable
        public final String classifier;
        @NotNull
        public final String type;
        @NotNull
        public final VersionRange requestedVersion;
        @NotNull
        public final Scope scope;
        @NotNull
        public final ExclusionContainer<Exclusion> edgeExclusion;
        @Nullable
        private DependencyLayerElement resolved;

        public DependencyEdge(@NotNull String group, @NotNull String artifact, @Nullable String classifier, @NotNull String type, @NotNull VersionRange requestedVersion, @NotNull Scope scope, @NotNull ExclusionContainer<Exclusion> edgeExclusion) {
            this.group = group;
            this.artifact = artifact;
            this.classifier = classifier;
            this.type = type;
            this.requestedVersion = requestedVersion;
            this.scope = scope;
            this.edgeExclusion = edgeExclusion;
        }

        DependencyEdge(@NotNull VersionlessDependency coordinate, @NotNull VersionRange requestedVersion, @NotNull Scope scope, @NotNull ExclusionContainer<Exclusion> edgeExclusion) {
            this(coordinate.group(), coordinate.artifact(), coordinate.classifier(), coordinate.getType("jar"), requestedVersion, scope, edgeExclusion);
        }

        @Contract(pure = true)
        public boolean isResolved() {
            return this.resolved != null;
        }

        @NotNull
        @Contract(pure = true)
        public DependencyLayerElement getResolved() {
            DependencyLayerElement resolved = this.resolved;
            if (resolved == null) {
                throw new IllegalStateException("Edge has not yet been resolved [" + this.group + ":" + this.artifact + "]! It is declared by " + (this.declarer != null ? this.getDeclarer().gav : "null"));
            }
            return resolved;
        }

        @Contract(pure = false, mutates = "this")
        void resolve(@NotNull DependencyLayerElement element) {
            if (this.resolved != null) {
                throw new IllegalStateException("Edge is already resolved!");
            }
            this.resolved = element;
        }

        @NotNull
        @Contract(pure = true)
        public DependencyLayerElement getDeclarer() {
            DependencyLayerElement declarer = this.declarer;
            if (declarer == null) {
                throw new IllegalStateException("This edge is not yet associated with a dependency layer element.");
            }
            return declarer;
        }

        @Override
        @NotNull
        public String toString() {
            return "DependencyLayerEdge"
                    + "[group=" + this.group
                    + " artifact=" + this.artifact
                    + " classifier=" + this.classifier
                    + " type=" + this.type
                    + " scope=" + this.scope
                    + " version=" + this.requestedVersion.toString()
                    + " exclusions=" + this.edgeExclusion.toString()
                    + "]";
        }
    }
}
