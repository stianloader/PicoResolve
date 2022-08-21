package de.geolykt.mavenresolver;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.CopyOnWriteArrayList;

import de.geolykt.mavenresolver.version.VersionRange;

@Deprecated
public class DependencyTreeNodeOld {

    public record Dependency(String group, String artifact, String classifier, String extension) {
    }

    private final DependencyTreeNodeOld parentNode;
    private final List<DependencyTreeNodeOld> children = new CopyOnWriteArrayList<>();
    private final ConcurrentMap<Dependency, VersionRange> dependencies = new ConcurrentHashMap<>();
    private final ConcurrentMap<Dependency, Set<Exclusion>> exclusions = new ConcurrentHashMap<>();
    private final ConcurrentMap<Dependency, Scope> scopes = new ConcurrentHashMap<>();
    private final GAV gav;
    private final int depth;

    public DependencyTreeNodeOld(GAV gav) {
        this(null, gav);
    }

    public DependencyTreeNodeOld(DependencyTreeNodeOld parent, GAV gav) {
        this.parentNode = parent;
        if (parent != null) {
            parent.children.add(this);
            this.depth = parent.depth + 1;
        } else {
            this.depth = 0;
        }
        this.gav = gav;
    }

    public DependencyTreeNodeOld getParentNode() {
        return parentNode;
    }

    public boolean hasDependency(Dependency dep) {
        return dependencies.containsKey(dep) || (parentNode != null && parentNode.hasDependency(dep));
    }

    private VersionRange getDefinedVersionChildren(Dependency dep) {
        List<DependencyTreeNodeOld> nextLayer = new ArrayList<>();
        List<DependencyTreeNodeOld> currentLayer = new ArrayList<>(children);

        while (!currentLayer.isEmpty()) {
            for (DependencyTreeNodeOld node : currentLayer) {
                VersionRange range = node.dependencies.get(dep);
                if (range != null) {
                    return range;
                }
                nextLayer.addAll(node.children);
            }
            currentLayer.clear();
            currentLayer.addAll(nextLayer);
            nextLayer.clear();
        }

        return null;
    }

    public VersionRange getDefinedVersion(Dependency dep) {
        if (parentNode != null) {
            VersionRange range = parentNode.getDefinedVersion(dep);
            if (range != null) {
                return range;
            }
        }
        VersionRange range =  dependencies.get(dep);
        if (range != null) {
            return range;
        }
        return getDefinedVersionChildren(dep);
    }

    public void defineDependency(Dependency dependency, VersionRange range, Set<Exclusion> exclusions, Scope scope) {
        this.dependencies.put(dependency, range);
        this.exclusions.put(dependency, exclusions);
        this.scopes.put(dependency, scope);
    }

    public GAV getGav() {
        return gav;
    }
}
