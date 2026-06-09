package org.stianloader.picoresolve;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.jetbrains.annotations.ApiStatus.AvailableSince;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.stianloader.picoresolve.exclusion.Exclusion;
import org.stianloader.picoresolve.exclusion.ExclusionContainer;
import org.stianloader.picoresolve.version.VersionRange;

public class DependencyLayer {
    public static class DependencyEdge {
        @NotNull
        public final String artifact;
        @Nullable
        public final String classifier;
        @Nullable
        private DependencyLayerElement declarer;
        @NotNull
        public final ExclusionContainer<Exclusion> edgeExclusion;
        @NotNull
        public final String group;
        @NotNull
        public final VersionRange requestedVersion;
        @Nullable
        private DependencyLayerElement resolved;
        @NotNull
        public final Scope scope;
        @NotNull
        public final String type;

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

        @NotNull
        @Contract(pure = true)
        public DependencyLayerElement getDeclarer() {
            DependencyLayerElement declarer = this.declarer;
            if (declarer == null) {
                throw new IllegalStateException("This edge is not yet associated with a dependency layer element.");
            }
            return declarer;
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

        @Contract(pure = true)
        public boolean isResolved() {
            return this.resolved != null;
        }

        @Contract(pure = false, mutates = "this")
        void resolve(@NotNull DependencyLayerElement element) {
            if (this.resolved != null) {
                throw new IllegalStateException("Edge is already resolved!");
            }
            this.resolved = element;
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

    public static class DependencyLayerElement {
        @Nullable
        public final String classifier;
        @NotNull
        public final GAV gav;
        @Nullable
        private DependencyLayer layer;
        @NotNull
        public final List<DependencyEdge> outgoingEdges;
        @NotNull
        public final ExclusionContainer<?> parentExclusions;
        @NotNull
        public final String type;

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

    /**
     * Create a new {@link DependencyLayer} with the given {@link GAV} as the root node.
     * The created {@link DependencyLayer} has no parent layer, making it most favourable for calls to
     * {@link MavenResolver#resolveAllChildren(DependencyLayer, java.util.concurrent.Executor)}.
     *
     * <p>The <code>rootNode</code> {@link GAV} is typically a bogus GAV and ignored within the
     * resolution process. However, sometimes it makes more sense to give it a sensical value such
     * as the declaring POM's coordinates, though this is mostly only for presentation reasons.
     *
     * <p>Dependency edges are created using the <code>jar</code> extension, without a classifier,
     * and the scope of {@link Scope#COMPILE}. Further, no exclusions are defined.
     *
     * @param rootNode The root node of the dependency layer, see {@link DependencyLayerElement#gav}.
     * @param dependencies The {@link GAV} coordinates of the dependencies to resolve.
     * @return The created {@link DependencyLayer}.
     * @since 1.1.1-a20260609
     */
    @NotNull
    @AvailableSince("1.1.1-a20260609")
    public static DependencyLayer createLayerFor(@NotNull GAV rootNode, @NotNull GAV @NotNull... dependencies) {
        List<DependencyEdge> virtualEdges = new ArrayList<>();

        for (GAV dependency : dependencies) {
            virtualEdges.add(new DependencyEdge(dependency.group(), dependency.artifact(), null, "jar", VersionRange.parse(dependency.version().getOriginText()), Scope.COMPILE, ExclusionContainer.empty()));
        }

        DependencyLayerElement virtualElement = new DependencyLayerElement(rootNode, null, null, ExclusionContainer.empty(), virtualEdges);

        return new DependencyLayer(null, Collections.singletonList(virtualElement));
    }

    @Nullable
    private DependencyLayer child;

    @NotNull
    public final List<@NotNull DependencyLayerElement> elements;

    @Nullable
    public final DependencyLayer parent;

    public DependencyLayer(@Nullable DependencyLayer parent, @NotNull List<@NotNull DependencyLayerElement> elements) {
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
}
