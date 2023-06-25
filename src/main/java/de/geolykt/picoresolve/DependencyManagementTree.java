package de.geolykt.picoresolve;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import de.geolykt.picoresolve.exclusion.Exclusion;
import de.geolykt.picoresolve.exclusion.ExclusionContainer;

class DependencyManagementTree {

    public static final DependencyManagementTree EMPTY = new DependencyManagementTree();

    private final List<DependencyManagementTree> imported = new ArrayList<>();
    private final Map<VersionlessDependency, DependencyManagementNode> nodes = new ConcurrentHashMap<>();
    private DependencyManagementTree parent;

    static class DependencyManagementNode {
        final String scope;
        final String range;
        final ExclusionContainer<Exclusion> exclusions;

        public DependencyManagementNode(String scope, String range, ExclusionContainer<Exclusion> exclusions) {
            this.scope = scope;
            this.range = range;
            this.exclusions = exclusions;
        }
    }

    public void addImportNode(DependencyManagementTree node) {
        this.imported.add(node);
    }

    public void addNode(VersionlessDependency dep, DependencyManagementNode node) {
        this.nodes.put(dep, node);
    }

    public void setParent(DependencyManagementTree parent) {
        if (this == DependencyManagementTree.EMPTY) {
            return;
        }
        this.parent = parent;
    }

    private boolean collectNodes0(Map<VersionlessDependency, DependencyManagementNode> out, int depth) {
        if (depth-- == 0) {
            for (Map.Entry<VersionlessDependency, DependencyManagementNode> entry : this.nodes.entrySet()) {
                out.putIfAbsent(entry.getKey(), entry.getValue());
            }
            return this.imported.isEmpty();
        }
        boolean empty = true;
        for (DependencyManagementTree importTree : this.imported) {
            empty &= importTree.collectNodes0(out, depth);
        }
        return empty;
    }

    public void collectNodes(Map<VersionlessDependency, DependencyManagementNode> out) {
        for (int depth = 0; !this.collectNodes0(out, depth); depth++) {
            // NOP
        }
        if (this.parent != null) {
            this.parent.collectNodes(out);
        }
    }
}
