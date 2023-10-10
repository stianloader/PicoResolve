package de.geolykt.picoresolve;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.ListIterator;
import java.util.Locale;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.Executor;

import org.dom4j.Document;
import org.dom4j.Element;
import org.dom4j.io.SAXReader;

import de.geolykt.picoresolve.DependencyLayer.DependencyEdge;
import de.geolykt.picoresolve.DependencyLayer.DependencyLayerElement;
import de.geolykt.picoresolve.DependencyManagementTree.DependencyManagementNode;
import de.geolykt.picoresolve.exclusion.Exclusion;
import de.geolykt.picoresolve.exclusion.ExclusionContainer;
import de.geolykt.picoresolve.exclusion.ExclusionContainer.ExclusionMode;
import de.geolykt.picoresolve.internal.ChildElementIterable;
import de.geolykt.picoresolve.internal.ConcurrencyUtil;
import de.geolykt.picoresolve.internal.StronglyMultiCompletableFuture;
import de.geolykt.picoresolve.internal.meta.VersionCatalogue;
import de.geolykt.picoresolve.internal.meta.VersionCatalogue.SnapshotVersion;
import de.geolykt.picoresolve.repo.MavenLocalRepositoryNegotiator;
import de.geolykt.picoresolve.repo.MavenRepository;
import de.geolykt.picoresolve.repo.RepositoryAttachedValue;
import de.geolykt.picoresolve.repo.RepositoryNegotiatior;
import de.geolykt.picoresolve.version.MavenVersion;
import de.geolykt.picoresolve.version.VersionRange;

public class MavenResolver {

    // TODO test tree resolving capabilities with https://repo1.maven.org/maven2/org/alfasoftware/astra/2.1.1/astra-2.1.1.pom
    // TODO cache VersionCatalogue objects
    // TODO cache poms
    private final RepositoryNegotiatior negotiator;
    private final ConcurrentMap<GAV, DependencyContainerNode> depdenencyCache = new ConcurrentHashMap<>();

    /**
     * Whether to pretend that dependencies with the "test" scope did not exist. This may significantly improve lookup speeds,
     * while in most cases not having any significant drawbacks due to the fact that usually you'd not want to resolve
     * the artifacts a dependency uses to test itself.
     */
    public boolean ignoreTestDependencies = true;

    /**
     * Whether to pretend that dependencies marked as "optional" do not exist. This may significantly improve lookup speeds,
     * while in most cases mirroring standard maven behaviour and being without drawbacks. However it may have drawbacks
     * when it comes to version negotiation.
     */
    public boolean ignoreOptionalDependencies = true;

    public MavenResolver(Path mavenLocal) {
        this(mavenLocal, null);
    }

    public MavenResolver(Path mavenLocal, Collection<MavenRepository> repos) {
        this.negotiator = new MavenLocalRepositoryNegotiator(mavenLocal);
        if (repos != null) {
            this.addRepositories(repos);
        }
    }

    public MavenResolver addRepositories(Collection<MavenRepository> repos) {
        repos.forEach(this::addRepository);
        return this;
    }

    public MavenResolver addRepository(MavenRepository repo) {
        this.negotiator.addRepository(repo);
        return this;
    }

    public MavenResolver addRepositories(MavenRepository... repos) {
        for (MavenRepository mr : repos) {
            addRepository(mr);
        }
        return this;
    }

    public CompletableFuture<RepositoryAttachedValue<Path>> download(GAV gav, String classifier, String extension, Executor executor) {
        CompletableFuture<RepositoryAttachedValue<Path>> resource;
        if (gav.version().getOriginText().toLowerCase(Locale.ROOT).endsWith("-snapshot")) {
            resource = this.downloadSnapshot(gav, classifier, extension, executor);
        } else {
            resource = ConcurrencyUtil.configureFallback(downloadSimple(gav, classifier, extension, executor), () -> {
                return this.downloadSnapshot(gav, classifier, extension, executor);
            });
        }
        return resource;
    }

    private static String applyPlaceholders(String string, int startIndex, Map<String, String> placeholders) {
        int indexStart = string.indexOf("${", startIndex);
        if (indexStart == -1) {
            return string;
        }
        int indexEnd = string.indexOf('}', indexStart);
        String property = string.substring(indexStart + 2, indexEnd);
        String replacement = placeholders.get(property);
        if (replacement == null) {
            return MavenResolver.applyPlaceholders(string, indexEnd, placeholders);
        }
        return string.substring(0, indexStart) + replacement + string.substring(indexEnd + 1);
    }

    private static String applyPlaceholders(String string, Map<String, String> placeholders) {
        if (string == null) {
            return null;
        }
        return MavenResolver.applyPlaceholders(string, 0, placeholders);
    }

    private static void extractProperties(Document project, GAV gav, Map<String, String> out) {
        for (Element elem : new ChildElementIterable(project.getRootElement())) {
            // See https://maven.apache.org/pom.html#properties (retrieved SEPT 18th 2022 18:19 CEST)
            // "project.x: A dot (.) notated path in the POM will contain the corresponding element's value."
            // For the sake of brevity, we only iterate over the top level of elements
            // While you might laugh, my gut is telling that checking more deeply nested elements might have unforeseen consequences.

            // https://maven.apache.org/guides/introduction/introduction-to-the-pom.html#available-variables (retrieved SEPT 25th 2022 16:49 CEST)
            // Defines that "pom.x" and "x" are allowed, even if they are discouraged (which does not prevent people from actually using them).
            // TODO as above document documents, implement "project.basedir", "project.baseUri" and "maven.build.timestamp".
            // Latter would be interesting...
            if (elem.isTextOnly()) {
                out.put("project." + elem.getQualifiedName(), elem.getText());
                out.put("pom." + elem.getQualifiedName(), elem.getText());
                out.put(elem.getQualifiedName(), elem.getText());
            }
        }
        Element properties = project.getRootElement().element("properties");
        if (properties != null) {
            for (Element prop : new ChildElementIterable(properties)) {
                out.put(prop.getName(), prop.getText());
            }
        }
    }

    private CompletableFuture<Document> downloadPom(GAV gav, Executor executor) {
        return download(gav, null, "pom", executor).thenApply((pathRAV) -> {
            try {
                SAXReader reader = new SAXReader();
                reader.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
                reader.setFeature("http://xml.org/sax/features/external-general-entities", false);
                reader.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
                Document xmlDoc;
                try (InputStream is = Files.newInputStream(pathRAV.getValue())) {
                    xmlDoc = reader.read(is);
                }
                xmlDoc.getRootElement().normalize();

                return xmlDoc;
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
    }

    private CompletableFuture<RepositoryAttachedValue<Path>> downloadSimple(GAV gav, String classifier, String extension, Executor executor) {
        String basePath = gav.group().replace('.', '/') + '/' + gav.artifact() + '/' + gav.version().getOriginText() + '/';
        String path = basePath + gav.artifact() + '-' + gav.version().getOriginText();
        if (classifier != null) {
            path += '-' + classifier;
        }
        path += '.' + extension;
        return this.negotiator.resolveStandard(path, executor);
    }

    private CompletableFuture<RepositoryAttachedValue<Path>> downloadSnapshot(GAV gav, String classifier, String extension, Executor executor) {
        String basePath = gav.group().replace('.', '/') + '/' + gav.artifact() + '/' + gav.version().getOriginText() + '/';

        return ConcurrencyUtil.configureFallback(this.negotiator.resolveMavenMeta(basePath + "maven-metadata.xml", executor).thenCompose(item -> {
            VersionCatalogue merged;
            List<VersionCatalogue> catalogues = new ArrayList<>();
            for (RepositoryAttachedValue<Path> rav : item) {
                try {
                    VersionCatalogue catalogue = new VersionCatalogue(Files.newInputStream(rav.getValue()));
                    catalogues.add(catalogue);
                } catch (Exception e) {
                    e.printStackTrace();
                    continue;
                }
            }
            merged = VersionCatalogue.merge(catalogues);
            for (SnapshotVersion snapshot : merged.snapshotVersions) {
                if (!snapshot.extension().equals(extension)) {
                    continue;
                }
                if (snapshot.classifier() != null && !snapshot.classifier().equals(classifier)) {
                    continue;
                }
                if (snapshot.classifier() == null && classifier != null) {
                    continue;
                }
                String path = basePath + gav.artifact() + '-' + snapshot.version();
                if (classifier != null) {
                    path += '-' + classifier;
                }
                path += '.' + extension;
                return this.negotiator.resolveStandard(path, executor);
            }
            if (merged.fallbackSnapshotVersion != null) {
                String path = basePath + gav.artifact() + '-' + merged.fallbackSnapshotVersion;
                if (classifier != null) {
                    path += '-' + classifier;
                }
                path += '.' + extension;
                return this.negotiator.resolveStandard(path, executor);
            }
            if (merged.localCopy) {
                String path = basePath + gav.artifact() + '-' + gav.version().getOriginText();
                if (classifier != null) {
                    path += '-' + classifier;
                }
                path += '.' + extension;
                return this.negotiator.resolveStandard(path, executor);
            }
            throw new IllegalStateException("Unable to find snapshot version");
        }), () -> {
            return this.downloadSimple(gav, classifier, extension, executor);
        });
    }

    private CompletableFuture<DependencyLayer> resolveChildLayer(DependencyLayer layer, Executor executor, Map<VersionlessDependency, DependencyLayerElement> resolveCache) {
        if (layer.getChild() != null) {
            throw new IllegalStateException("Child layer already resolved");
        }
        class ChildResolutionContext {
            VersionRange range = VersionRange.FREE_RANGE;
            Scope scope;
            final List<DependencyEdge> declaringEdges = new ArrayList<>();
            final ExclusionContainer<ExclusionContainer<?>> effectiveExclusions = new ExclusionContainer<>(ExclusionMode.ALL);
        }
        Map<VersionlessDependency, ChildResolutionContext> resolveChildren = new HashMap<>();
        for (DependencyLayerElement element : layer.elements) {
            for (DependencyEdge edge : element.outgoingEdges) {
                VersionlessDependency dep = new VersionlessDependency(edge.group, edge.artifact, edge.classifier, edge.type);
                if (resolveCache.containsKey(dep)) {
                    // Given that the artifact this edge is pointing to has already been resolved in a previous (parent) layer,
                    // we can resolve this edge now and exclude it from the version negotiation and child resolution process.
                    DependencyLayerElement cache = resolveCache.get(dep);
                    if (cache != null && !edge.isResolved()) {
                        edge.resolve(cache);
                    }
                    continue;
                }
                ChildResolutionContext ctx = resolveChildren.get(dep);
                if (ctx == null) {
                    ctx = new ChildResolutionContext();
                    resolveChildren.put(dep, ctx);
                }
                ctx.range = ctx.range.intersect(edge.requestedVersion);
                if (ctx.scope == null) {
                    ctx.scope = edge.scope;
                } else if (edge.scope == Scope.COMPILE) {
                    ctx.scope = Scope.COMPILE;
                } else if (ctx.scope == Scope.TEST) {
                    ctx.scope = edge.scope;
                } else if (edge.scope == Scope.PROVIDED && ctx.scope == Scope.RUNTIME) {
                    ctx.scope = Scope.PROVIDED;
                }
                ctx.effectiveExclusions.addChild(new ExclusionContainer<>(ExclusionMode.ANY, Arrays.asList(element.parentExclusions, edge.edgeExclusion), false));
                ctx.declaringEdges.add(edge);
            }
        }

        List<CompletableFuture<DependencyLayerElement>> futures = new ArrayList<>();
        for (Map.Entry<VersionlessDependency, ChildResolutionContext> entry : resolveChildren.entrySet()) {
            VersionlessDependency coordinates = entry.getKey();
            ChildResolutionContext resolveContext = entry.getValue();
            futures.add(this.getVersions(coordinates.group(), coordinates.artifact(), executor).thenCompose((catalogue)-> {
                MavenVersion selected = catalogue.selectVersion(resolveContext.range);
                if (selected == null) {
                    System.err.println("Catastrophic resultion failure for " + coordinates + ":" + resolveContext.range);
                    throw new IllegalStateException("Unable to resolve a sensical version for range " + resolveContext.range + " for coordinates " + coordinates);
                }
                GAV gav = new GAV(coordinates.group(), coordinates.artifact(), selected);
                return this.getNode(gav, coordinates.classifier(), coordinates.type(), executor);
            }).thenApply((node) -> {
                DependencyLayerElement element = node.toLayerElement(coordinates.classifier(), coordinates.type(), resolveContext.effectiveExclusions);
                for (DependencyEdge edge : resolveContext.declaringEdges) {
                    edge.resolve(element);
                }
                return element;
            }));
        }

        if (futures.size() == 0) {
            return CompletableFuture.completedFuture(null);
        }

        StronglyMultiCompletableFuture<DependencyLayerElement> combinedFuture = new StronglyMultiCompletableFuture<>(futures);
        return combinedFuture.thenApply((elements) -> {
            combinedFuture.throwExceptionIfCompletedUncleanly();
            return new DependencyLayer(layer, Collections.unmodifiableList(elements));
        });
    }

    private CompletableFuture<Void> resolveAllChildren0(DependencyLayer layer, Executor executor, Map<VersionlessDependency, DependencyLayerElement> resolveCache) {
        System.err.println("Resolving layer with dependency elements " + layer.elements); // TODO Debug statement

        return this.resolveChildLayer(layer, executor, resolveCache).thenCompose((child) -> {
            if (child == null) {
                return CompletableFuture.completedFuture(null);
            } else {
                for (DependencyLayerElement element : child.elements) {
                    resolveCache.put(new VersionlessDependency(element.gav.group(), element.gav.artifact(), element.classifier, element.type), element);
                }
                return this.resolveAllChildren0(child, executor, resolveCache);
            }
        });
    }

    public CompletableFuture<Void> resolveAllChildren(DependencyLayer current, Executor executor) {
        Map<VersionlessDependency, DependencyLayerElement> resolveCache = new HashMap<>();
        for (DependencyLayer layer = current; layer != null; layer = layer.parent) {
            for (DependencyLayerElement element : layer.elements) {
                resolveCache.put(new VersionlessDependency(element.gav.group(), element.gav.artifact(), element.classifier, element.type), element);
            }
        }
        return this.resolveAllChildren0(current, executor, resolveCache);
    }

    public CompletableFuture<DependencyLayer> resolveChildLayer(DependencyLayer current, Executor executor) {
        Map<VersionlessDependency, DependencyLayerElement> resolveCache = new HashMap<>();
        for (DependencyLayer layer = current; layer != null; layer = layer.parent) {
            for (DependencyLayerElement element : layer.elements) {
                resolveCache.put(new VersionlessDependency(element.gav.group(), element.gav.artifact(), element.classifier, element.type), element);
            }
        }
        return this.resolveChildLayer(current, executor, resolveCache);
    }

    private CompletableFuture<DependencyContainerNode> getNode(GAV gav, String classifier, String type, Executor executor) {
        DependencyContainerNode node = this.depdenencyCache.get(gav);
        if (node != null) {
            return CompletableFuture.completedFuture(node);
        }
        return this.downloadPom(gav, executor).thenCompose((xmlDoc) -> {
            List<Map.Entry<GAV, Document>> list = new ArrayList<>();
            list.add(new AbstractMap.SimpleImmutableEntry<>(gav, xmlDoc));
            Element project = xmlDoc.getRootElement();
            project.normalize();
            Element parent = project.element("parent");
            if (parent == null) {
                return CompletableFuture.completedFuture(list);
            } else {
                return this.downloadParentPoms(parent, executor, list);
            }
        }).thenCompose((poms) -> {
            Map<String, String> placeholders = new HashMap<>();
            MavenResolver.computePlaceholders(poms, 0, placeholders);
            return this.getDependencyManagementTree(executor, poms, 0).thenApply((depManagement) -> {
                return getDependencyNode0(placeholders, poms, depManagement);
            });
        });
    }

    private DependencyContainerNode getDependencyNode0(Map<String, String> placeholders, List<Entry<GAV, Document>> poms, DependencyManagementTree dependencyManagement) {
        Document xmlDoc = poms.get(0).getValue();
        DependencyContainerNode container = new DependencyContainerNode(poms.get(0).getKey());
        Element project = xmlDoc.getRootElement();
        Element deps = project.element("dependencies");
        if (deps == null) {
            return container;
        }

        Map<VersionlessDependency, DependencyManagementNode> managementNodes = new HashMap<>();
        dependencyManagement.collectNodes(managementNodes);

        for (Element dependency : deps.elements()) {
            String group = dependency.elementText("groupId");
            String artifactId = dependency.elementText("artifactId");
            String version = dependency.elementText("version");
            String scope = dependency.elementText("scope");
            String classifier = dependency.elementText("classifier");
            String type = dependency.elementText("type");
            String optional = dependency.elementText("optional"); // TODO implement
            ExclusionContainer<Exclusion> exclusions = MavenResolver.parseExclusions(dependency.element("exclusions"), placeholders);

            group = MavenResolver.applyPlaceholders(group, placeholders);
            artifactId = MavenResolver.applyPlaceholders(artifactId, placeholders);
            version = MavenResolver.applyPlaceholders(version, placeholders);
            scope = MavenResolver.applyPlaceholders(scope, placeholders);
            classifier = MavenResolver.applyPlaceholders(classifier, placeholders);
            type = MavenResolver.applyPlaceholders(type, placeholders);
            optional = MavenResolver.applyPlaceholders(optional, placeholders);

            DependencyManagementNode managementNode = managementNodes.get(new VersionlessDependency(group, artifactId, classifier, type));
            if (managementNode != null) {
                if (version == null) {
                    version = managementNode.range;
                }
                if (scope == null) {
                    scope = managementNode.scope;
                }
                if (exclusions == null) {
                    // TODO what is the behaviour there? Does it merge the exclusions or just overwrite them if not already existing?
                    exclusions = managementNode.exclusions;
                }
            }

            if (this.ignoreTestDependencies && "test".equalsIgnoreCase(scope)) {
                continue;
            }
            if (this.ignoreOptionalDependencies && "true".equalsIgnoreCase(optional)) {
                continue;
            }

            if (version == null) {
                throw new IllegalStateException("Fatal failure while assembling dependency " + group + ":" + artifactId + ":" + classifier + ":" + type + " as defined by " + poms.get(0).getKey() + ". This likely hints at either an impoper POM or incorrect dependency management parsing by the resolver.");
            }

            container.createDependency(group, artifactId, classifier, type, VersionRange.parse(version), Scope.fromString(scope), exclusions);
        }
        return container;
    }

    private CompletableFuture<List<Map.Entry<GAV, Document>>> downloadParentPoms(Element parentElement, Executor executor, List<Map.Entry<GAV, Document>> sink) {
        String group = parentElement.elementText("groupId");
        String artifactId = parentElement.elementText("artifactId");
        String version = parentElement.elementText("version");

        GAV gav = new GAV(group, artifactId, MavenVersion.parse(version));

        // TODO Implement in-memory document cache to prevent downloading duplicate parent POMs
        return downloadPom(gav, executor).thenCompose((xmlDoc) -> {
            synchronized(sink) {
                sink.add(new AbstractMap.SimpleImmutableEntry<>(gav, xmlDoc));
            }
            Element project = xmlDoc.getRootElement();
            project.normalize();
            Element parent = project.element("parent");
            if (parent != null) {
                return this.downloadParentPoms(parent, executor, sink);
            }
            return CompletableFuture.completedFuture(sink);
        });
    }

    private static void computePlaceholders(List<Map.Entry<GAV, Document>> poms, int pomIndex, Map<String, String> out) {
        GAV gav = poms.get(pomIndex).getKey();
        if (!poms.isEmpty()) {
            for (ListIterator<Map.Entry<GAV, Document>> lit = poms.listIterator(pomIndex); lit.hasNext();) { // TODO chances are we need the inverse order (we used to iterate from the back), so be aware that this may need fixing
                Map.Entry<GAV, Document> entry = lit.next();
                MavenResolver.extractProperties(entry.getValue(), entry.getKey(), out);
            }
        }

        // Then we also apply optional project.* placeholders that are inherited from the parent pom (you gotta be kidding me)
        // We might also need to identify inheritance
        out.put("project.version", gav.version().getOriginText());
        out.put("project.groupId", gav.group());
        out.put("pom.version", gav.version().getOriginText());
        out.put("pom.groupId", gav.group());
        out.put("version", gav.version().getOriginText());
        out.put("groupId", gav.group());
    }

    private CompletableFuture<DependencyManagementTree> getDependencyManagementBOMTree(Executor executor, String group, String artifact, VersionRange version, DependencyManagementTree parentNode) {
        return this.downloadPom(group, artifact, version, executor).thenCompose((entry) -> {
            Document xmlDoc = entry.getValue();
            List<Map.Entry<GAV, Document>> list = new ArrayList<>();
            list.add(entry);
            Element project = xmlDoc.getRootElement();
            project.normalize();
            Element parent = project.element("parent");
            if (parent == null) {
                return CompletableFuture.completedFuture(list);
            } else {
                return this.downloadParentPoms(parent, executor, list);
            }
        }).thenCompose((poms) -> {
            return getDependencyManagementTree(executor, poms, 0);
        }).thenApply((node) -> {
            parentNode.addImportNode(node);
            return node;
        });
    }

    private CompletableFuture<DependencyManagementTree> getDependencyManagementTree(Executor executor, List<Map.Entry<GAV, Document>> poms, int pomIndex) {
        Map<String, String> placeholders = new HashMap<>();
        MavenResolver.computePlaceholders(poms, pomIndex, placeholders);

        Element project = poms.get(pomIndex).getValue().getRootElement();
        Element dependencyManagement = project.element("dependencyManagement");
        int parentPomIndex = pomIndex + 1;
        Element dependencies;

        if (dependencyManagement == null) {
            dependencies = null;
        } else {
            dependencies = dependencyManagement.element("dependencies");
        }

        if (dependencies == null) {
            if (parentPomIndex == poms.size()) {
                // No further parents
                return CompletableFuture.completedFuture(DependencyManagementTree.EMPTY);
            }
            return getDependencyManagementTree(executor, poms, parentPomIndex).thenApply((parentTree) -> {
                // You might think - surely, you can just return the parent tree?
                // Well, you'd be wrong as the depth of BOM (bill-of-materials) nodes matter.
                DependencyManagementTree tree = new DependencyManagementTree();
                tree.setParent(parentTree);
                return tree;
            });
        } else {
            DependencyManagementTree tree = new DependencyManagementTree();
            List<CompletableFuture<DependencyManagementTree>> dependencyFutures = new ArrayList<>();
            for (Element dependency : dependencies.elements()) {
                String group = dependency.elementText("groupId");
                String artifactId = dependency.elementText("artifactId");
                String version = dependency.elementText("version");
                String scope = dependency.elementText("scope");
                String classifier = dependency.elementText("classifier");
                String type = dependency.elementText("type");
                String optional = dependency.elementText("optional"); // TODO is that inherited too?
                ExclusionContainer<Exclusion> exclusions = MavenResolver.parseExclusions(dependency.element("exclusions"), placeholders);

                group = MavenResolver.applyPlaceholders(group, placeholders);
                artifactId = MavenResolver.applyPlaceholders(artifactId, placeholders);
                version = MavenResolver.applyPlaceholders(version, placeholders);
                scope = MavenResolver.applyPlaceholders(scope, placeholders);
                classifier = MavenResolver.applyPlaceholders(classifier, placeholders);
                type = MavenResolver.applyPlaceholders(type, placeholders);
                optional = MavenResolver.applyPlaceholders(optional, placeholders);

                if (scope != null && scope.equals("import")) {
                    DependencyManagementTree importNode = new DependencyManagementTree();
                    tree.addImportNode(importNode);
                    dependencyFutures.add(this.getDependencyManagementBOMTree(executor, group, artifactId, VersionRange.parse(version), importNode));
                } else {
                    tree.addNode(new VersionlessDependency(group, artifactId, classifier, type), new DependencyManagementNode(scope, version, exclusions));
                }
            }

            if (parentPomIndex == poms.size()) {
                return CompletableFuture.completedFuture(tree);
            } else {
                return this.getDependencyManagementTree(executor, poms, parentPomIndex).thenCompose((parentDependencyManagement) -> {
                    parentDependencyManagement.setParent(tree);
                    return new StronglyMultiCompletableFuture<>(dependencyFutures);
                }).thenApply((ignore) -> tree);
            }
        }
    }

    public CompletableFuture<Map.Entry<GAV, RepositoryAttachedValue<Path>>> download(String group, String artifact, VersionRange versionRange, String classifier, String extension, Executor executor) {
        return this.getVersions(group, artifact, executor).thenCompose((catalogue)-> {
            MavenVersion selected = catalogue.selectVersion(versionRange);
            GAV gav = new GAV(group, artifact, selected);
            return this.download(gav, classifier, extension, executor).thenApply((rav) -> {
                return new AbstractMap.SimpleImmutableEntry<>(gav, rav);
            });
        });
    }

    private CompletableFuture<VersionCatalogue> getVersions(String groupId, String artifactId, Executor executor) {
        return this.negotiator.resolveMavenMeta(groupId.replace('.', '/') + '/' + artifactId + "/maven-metadata.xml", executor).thenApply((item) -> {

            List<VersionCatalogue> catalogues = new ArrayList<>(item.size());
            for (RepositoryAttachedValue<Path> rav : item) {
                VersionCatalogue catalogue;
                try (InputStream is = Files.newInputStream(rav.getValue())) {
                    catalogue = new VersionCatalogue(is);
                } catch (Exception e) {
                    e.printStackTrace();
                    continue;
                }
                catalogues.add(catalogue);
            }
            return VersionCatalogue.merge(catalogues);
        });
    }

    private CompletableFuture<Map.Entry<GAV, Document>> downloadPom(String group, String artifact, VersionRange range, Executor executor) {
        return this.download(group, artifact, range, null, "pom", executor).thenApply((entry) -> {
            try {
                SAXReader reader = new SAXReader();
                reader.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
                reader.setFeature("http://xml.org/sax/features/external-general-entities", false);
                reader.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
                Document xmlDoc;
                try (InputStream is = Files.newInputStream(entry.getValue().getValue())) {
                    xmlDoc = reader.read(is);
                }
                xmlDoc.getRootElement().normalize();
                return new AbstractMap.SimpleImmutableEntry<>(entry.getKey(), xmlDoc);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
    }

    private static ExclusionContainer<Exclusion> parseExclusions(Element element, Map<String, String> placeholders) {
        if (element == null) {
            return null;
        }
        List<Element> exclusions = element.elements();
        List<Exclusion> parsed = new ArrayList<>(exclusions.size());
        for (Element exclusion : exclusions) {
            String group = exclusion.elementText("groupId");
            String artifact = exclusion.elementText("artifactId");
            group = MavenResolver.applyPlaceholders(group, placeholders);
            artifact = MavenResolver.applyPlaceholders(artifact, placeholders);

            parsed.add(new Exclusion(group, artifact));
        }
        return new ExclusionContainer<>(ExclusionMode.ANY, parsed, false);
    }
}
