package de.geolykt.mavenresolver;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.ListIterator;
import java.util.Locale;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import org.dom4j.Document;
import org.dom4j.Element;
import org.dom4j.io.SAXReader;

import de.geolykt.mavenresolver.DependencyContainerNode.DependencyNode;
import de.geolykt.mavenresolver.internal.ChildElementIterable;
import de.geolykt.mavenresolver.internal.ConcurrencyUtil;
import de.geolykt.mavenresolver.internal.ConfusedResolverException;
import de.geolykt.mavenresolver.internal.ObjectSink;
import de.geolykt.mavenresolver.internal.meta.VersionCatalogue;
import de.geolykt.mavenresolver.internal.meta.VersionCatalogue.SnapshotVersion;
import de.geolykt.mavenresolver.repo.MavenLocalRepositoryNegotiator;
import de.geolykt.mavenresolver.repo.MavenRepository;
import de.geolykt.mavenresolver.repo.RepositoryAttachedValue;
import de.geolykt.mavenresolver.repo.RepositoryNegotiatior;
import de.geolykt.mavenresolver.version.MavenVersion;
import de.geolykt.mavenresolver.version.VersionRange;

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
        if (repos != null) {
            addRepositories(repos);
        }
        this.negotiator = new MavenLocalRepositoryNegotiator(mavenLocal);
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

    private void getVersions(String groupId, String artifactId, Executor executor, ObjectSink<VersionCatalogue> sink) {
        String mavenMetadataPath = groupId.replace('.', '/') + '/' + artifactId + "/maven-metadata.xml";

        CompletableFuture<List<RepositoryAttachedValue<Path>>> mavenMetadataFuture =  this.negotiator.resolveMavenMeta(mavenMetadataPath, executor);
        mavenMetadataFuture.exceptionally(ex -> {
            sink.onError(ex);
            return null;
        });
        mavenMetadataFuture.thenAccept(item -> {
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
            sink.nextItem(VersionCatalogue.merge(catalogues));
            sink.onComplete();
        });
    }

    public CompletableFuture<RepositoryAttachedValue<Path>> download(GAV gav, String classifier, String extension, Executor executor) {
        CompletableFuture<RepositoryAttachedValue<Path>> resource;
        if (gav.version().getOriginText().toLowerCase(Locale.ROOT).endsWith("-snapshot")) {
            resource = downloadSnapshot(gav, classifier, extension, executor);
        } else {
            resource = ConcurrencyUtil.configureFallback(downloadSimple(gav, classifier, extension, executor), () -> {
                return downloadSnapshot(gav, classifier, extension, executor);
            });
        }

        return resource;
    }

    private void downloadParentPoms(Element parentElement, Executor executor, ObjectSink<Map.Entry<GAV, Document>> sink) {
        if (parentElement == null) {
            sink.onComplete();
            return;
        }
        String group = parentElement.elementText("groupId");
        String artifactId = parentElement.elementText("artifactId");
        String version = parentElement.elementText("version");

        GAV gav = new GAV(group, artifactId, MavenVersion.parse(version));
        download(gav, null, "pom", executor).thenAccept((pathRAV) -> {
            try {
                SAXReader reader = new SAXReader();
                reader.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
                reader.setFeature("http://xml.org/sax/features/external-general-entities", false);
                reader.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
                Document xmlDoc;
                try (InputStream is = Files.newInputStream(pathRAV.getValue())) {
                    xmlDoc = reader.read(is);
                }
                Element project = xmlDoc.getRootElement();
                project.normalize();

                sink.nextItem(new AbstractMap.SimpleImmutableEntry<>(gav, xmlDoc));

                Element parent = project.element("parent");

                if (parent == null) {
                    sink.onComplete();
                    return;
                } else {
                    downloadParentPoms(parent, executor, sink);
                }
            } catch (Exception e) {
                if (e instanceof RuntimeException) {
                    throw (RuntimeException) e;
                }
                throw new RuntimeException(e);
            }
        });
    }

    private String applyPlaceholders(String string, int startIndex, Map<String, String> placeholders) {
        int indexStart = string.indexOf("${", startIndex);
        if (indexStart == -1) {
            return string;
        }
        int indexEnd = string.indexOf('}', indexStart);
        String property = string.substring(indexStart + 2, indexEnd);
        String replacement = placeholders.get(property);
        if (replacement == null) {
            return applyPlaceholders(string, indexEnd, placeholders);
        }
        return string.substring(0, indexStart) + replacement + string.substring(indexEnd + 1);
    }

    private String applyPlaceholders(String string, Map<String, String> placeholders) {
        if (string == null) {
            return null;
        }
        return applyPlaceholders(string, 0, placeholders);
    }

    private void extractProperties(Document project, GAV gav, Map<String, String> out) {
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
                out.put("project." + elem.getPath(), elem.getText());
                out.put("pom." + elem.getPath(), elem.getText());
                out.put(elem.getPath(), elem.getText());
            }
        }
        Element properties = project.getRootElement().element("properties");
        if (properties != null) {
            for (Element prop : new ChildElementIterable(properties)) {
                out.put(prop.getName(), prop.getText());
            }
        }
    }

    private void computePlaceholders(GAV gav, Document pom, List<Map.Entry<GAV, Document>> parentPom, Map<String, String> out) {
        if (!parentPom.isEmpty()) {
            for (ListIterator<Map.Entry<GAV, Document>> lit = parentPom.listIterator(parentPom.size()); lit.hasPrevious();) {
                Map.Entry<GAV, Document> entry = lit.previous();
                extractProperties(entry.getValue(), entry.getKey(), out);
            }
        }
        extractProperties(pom, gav, out);

        // Then we also apply optional project.* placeholders that are inherited from the parent pom (you gotta be kidding me)
        // We might also need to identify inheritance
        out.put("project.version", gav.version().getOriginText());
        out.put("project.groupId", gav.group());
        out.put("pom.version", gav.version().getOriginText());
        out.put("pom.groupId", gav.group());
        out.put("version", gav.version().getOriginText());
        out.put("groupId", gav.group());
    }

    private void readDependencies(Element dependencyBlock, Map<String, String> placeholders, Map<VersionlessDependency, VersionRange> fallbackVersions, DependencyContainerNode node) {
        for (Element dependency : new ChildElementIterable(dependencyBlock)) {

            String group = dependency.elementText("groupId");
            String artifactId = dependency.elementText("artifactId");
            String version = dependency.elementText("version");
            String scope = dependency.elementText("scope");
            String classifier = dependency.elementText("classifier");
            String type = dependency.elementText("type");
            String optional = dependency.elementText("optional");

            group = applyPlaceholders(group, placeholders);
            artifactId = applyPlaceholders(artifactId, placeholders);
            version = applyPlaceholders(version, placeholders);
            scope = applyPlaceholders(scope, placeholders);
            classifier = applyPlaceholders(classifier, placeholders);
            type = applyPlaceholders(type, placeholders);
            optional = applyPlaceholders(optional, placeholders);

            if (ignoreTestDependencies && scope != null && scope.equalsIgnoreCase("test")) {
                continue;
            }
            if (ignoreOptionalDependencies && "true".equalsIgnoreCase(optional)) {
                continue;
            }

            DependencyNode dep = node.getOrCreateDependency(group, artifactId, classifier, type);

            if (version != null) {
                VersionRange range = VersionRange.parse(version);
                if (dep.version != null && !range.equals(dep.version)) {
                    throw new ConfusedResolverException(dep.version + " and " + range + " collide for dependency " + group + ":" + artifactId);
                }
                if (dep.version == null) {
                    dep.version = range;
                }
            } else if (dep.version == null) {
                dep.version = fallbackVersions.get(new VersionlessDependency(group, artifactId, classifier, type == null ? "jar" : type));
                if (dep.version == null) {
                    throw new IllegalStateException("Version not found for " + dep.group + ':' + dep.artifact + ':' + dep.classifier + ':' + dep.type
                            + " declared by " + node.gav);
                }
            }

            if (scope != null) {
                dep.scopes.add(Scope.valueOf(scope.toUpperCase(Locale.ROOT)));
            }

            // TODO multiple dependency blocks with different scopes but different exclusions result in behaviour
            //      that is not being imitated by below code block
            Element exclusions = dependency.element("exclusions");
            if (exclusions != null) {
                for (Element exclusion : new ChildElementIterable(exclusions)) {
                    dep.addExclusion(new Exclusion(applyPlaceholders(exclusion.elementText("groupId"), placeholders), applyPlaceholders(exclusion.elementText("artifactId"), placeholders)));
                }
            }
        }
    }

    private void fillInDependencyManagement(GAV gav, Executor executor, Document pom, List<Map.Entry<GAV, Document>> parentPoms, int parentPomIndex, ConcurrentMap<VersionlessDependency, VersionRange> out, Runnable onDone) {
        Map<String, String> placeholders = new HashMap<>();
        computePlaceholders(gav, pom, parentPoms, placeholders);

        AtomicBoolean intermediaryTaskDone = new AtomicBoolean();
        boolean hasDependencyManagement = false;

        Element project = pom.getRootElement();
        Element dependencyManagement = project.element("dependencyManagement");
        if (dependencyManagement != null) {
            Element dependencies = dependencyManagement.element("dependencies");
            if (dependencies != null) {
                // TODO Can BOM (Bill-of-materials) POMs inherit their own dependency management blocks from the parent too? If so, it'd be annoying because that would need to be implemented in it's own method
                handleDependencyManagementDeps(executor, dependencies, placeholders, out, () -> {
                    if (!intermediaryTaskDone.compareAndSet(false, true)) {
                        onDone.run();
                    }
                });
                hasDependencyManagement = true;
            }
        }

        if (parentPomIndex == parentPoms.size()) {
            // There is no parent, so we can call the runnable if it needs to be run (i.e. because either the dependency management block was already evaluated or because there is no dependency management block)
            if (!hasDependencyManagement || !intermediaryTaskDone.compareAndSet(false, true)) {
                onDone.run();
            }
            return;
        }

        GAV parentGAV = parentPoms.get(parentPomIndex).getKey();
        Document parentPom = parentPoms.get(parentPomIndex).getValue();

        if (hasDependencyManagement) {
            fillInDependencyManagement(parentGAV, executor, parentPom, parentPoms, parentPomIndex + 1, out, () -> {
                if (!intermediaryTaskDone.compareAndSet(false, true)) {
                    onDone.run();
                }
            });
        } else {
            fillInDependencyManagement(parentGAV, executor, parentPom, parentPoms, parentPomIndex + 1, out, onDone::run);
        }
    }

    private CompletableFuture<Void> fillInDependencies(GAV gav, Executor executor, Document pom, List<Map.Entry<GAV, Document>> parentPoms, DependencyContainerNode node) {
        Map<String, String> placeholders = new HashMap<>();
        ConcurrentMap<VersionlessDependency, VersionRange> dependencyVersions = new ConcurrentHashMap<>();
        computePlaceholders(gav, pom, parentPoms, placeholders);

        CompletableFuture<Void> cf = new CompletableFuture<>();
        fillInDependencyManagement(gav, executor, pom, parentPoms, 0, dependencyVersions, () -> {
            Element dependencies = pom.getRootElement().element("dependencies");
            if (dependencies != null) {
                readDependencies(dependencies, placeholders, dependencyVersions, node);
            }
            cf.complete(null);
        });
        return cf;
    }

    private void handleDependencyManagementDeps(Executor executor, Element dependencyBlock, Map<String, String> placeholders, Map<VersionlessDependency, VersionRange> out, Runnable onDone) {
        AtomicInteger scheduledTasks = new AtomicInteger();
        AtomicBoolean guard = new AtomicBoolean(true);
        for (Element dependency : new ChildElementIterable(dependencyBlock)) {
            String group = dependency.elementText("groupId");
            String artifactId = dependency.elementText("artifactId");
            String version = dependency.elementText("version");
            String scope = dependency.elementText("scope");
            String classifier = dependency.elementText("classifier");
            String type = dependency.elementText("type");

            group = applyPlaceholders(group, placeholders);
            artifactId = applyPlaceholders(artifactId, placeholders);
            version = applyPlaceholders(version, placeholders);
            scope = applyPlaceholders(scope, placeholders);
            classifier = applyPlaceholders(classifier, placeholders);
            type = applyPlaceholders(type, placeholders);

            if (scope != null && scope.equalsIgnoreCase("import")) {
                if (type == null || !type.equals("pom")) {
                    throw new IllegalStateException("Type must be \"pom\" for the import scope, but it is " + type);
                }
                if (classifier != null) {
                    throw new IllegalStateException("Cannot have an classifier when using the import scope!");
                }
                VersionRange range = VersionRange.parse(version);
                MavenVersion mversion = range.getRecommended();
                if (mversion == null) {
                    // At least there is that... HOPEFULLY it isn't a bug within m2e
                    throw new IllegalStateException("Cannot use version ranges within the depencency management block!");
                } else if (mversion.getOriginText().indexOf('{') != -1) {
                    throw new IllegalStateException("Invalid recommended maven version: " + mversion);
                }
                GAV importedGAV = new GAV(group, artifactId, mversion);
                scheduledTasks.incrementAndGet();
                downloadPom(importedGAV, executor).thenAccept((item) -> {
                    Element project = item.getRootElement();
                    Element dependencyManagement = project.element("dependencyManagement");
                    if (dependencyManagement == null) {
                        if (scheduledTasks.decrementAndGet() == 0 && guard.compareAndSet(false, true)) {
                            onDone.run();
                        }
                        return; // What a waste of CPU time
                    }
                    Element dependencies = dependencyManagement.element("dependencies");
                    if (dependencies == null) {
                        if (scheduledTasks.decrementAndGet() == 0 && guard.compareAndSet(false, true)) {
                            onDone.run();
                        }
                        return;
                    }

                    Element parent = project.element("parent");
                    if (parent == null) {
                        Map<String, String> placeholdersImported = new HashMap<>();
                        computePlaceholders(importedGAV, item, Collections.emptyList(), placeholdersImported);
                        handleDependencyManagementDeps(executor, dependencies, placeholdersImported, out, () -> {
                            if (scheduledTasks.decrementAndGet() == 0 && guard.compareAndSet(false, true)) {
                                onDone.run();
                            }
                        });
                    } else {
                        downloadParentPoms(parent, executor, new ObjectSink<>() {

                            private volatile boolean done;
                            private List<Entry<GAV, Document>> parentPoms = new ArrayList<>();

                            @Override
                            public void onError(Throwable error) {
                                if (done) {
                                    return;
                                }
                                done = true;
                                // TODO find a way to not absorb the error here
                                if (scheduledTasks.decrementAndGet() == 0 && guard.compareAndSet(false, true)) {
                                    onDone.run();
                                }
                            }

                            @Override
                            public void nextItem(Entry<GAV, Document> item) {
                                parentPoms.add(item);
                            }

                            @Override
                            public void onComplete() {
                                if (done) {
                                    return;
                                }
                                done = true;
                                Map<String, String> placeholdersImported = new HashMap<>();
                                computePlaceholders(importedGAV, item, parentPoms, placeholdersImported);
                                handleDependencyManagementDeps(executor, dependencies, placeholdersImported, out, () -> {
                                    if (scheduledTasks.decrementAndGet() == 0 && guard.compareAndSet(false, true)) {
                                        onDone.run();
                                    }
                                });
                            }
                        });
                    }
                });
            } else {
                // provided, runtime, test and compile are all treated identically
                // system is an outlier here, but that scope is not supported by us
                if (type == null) {
                    type = "jar";
                }
                VersionRange newRange = VersionRange.parse(version);
                out.compute(new VersionlessDependency(group, artifactId, classifier, type), (key, oldRange) -> {
                    if (oldRange == null) {
                        return newRange;
                    } else {
                        return oldRange.intersect(newRange);
                    }
                });
            }
        }

        guard.set(false);
        if (scheduledTasks.get() == 0 && guard.compareAndSet(false, true)) {
            onDone.run();
        }
    }

    private void generateDepenents(List<DependencyContainerNode> nodes, ConcurrentMap<VersionlessDependency, MavenVersion> negotiatedCache, Executor executor, ObjectSink<DependencyContainerNode> sink) {
        if (nodes.isEmpty()) {
            throw new IllegalArgumentException("Argument \"nodes\" is an empty list. What is the point of this method call?");
        }
        List<DependencyContainerNode> subnodes = new CopyOnWriteArrayList<>();
        getDependents(nodes, negotiatedCache, subnodes, executor, () -> {
            subnodes.forEach(sink::nextItem);
            if (subnodes.isEmpty()) {
                sink.onComplete();
            } else {
                generateDepenents(subnodes, negotiatedCache, executor, sink);
            }
        });
    }

    private DependencyTree assembleFullTree(DependencyContainerNode rootNode, String rootClassifier, String rootType, List<DependencyContainerNode> dependencies, Map<GAV, DependencyTree> recursionStop) {
        DependencyTree cachedTree = recursionStop.get(rootNode.gav);
        if (cachedTree != null) {
            return cachedTree;
        }
        List<DependencyTree> children = new ArrayList<>();
        DependencyTree tree = new DependencyTree(rootNode.gav, rootClassifier, rootType, children);
        recursionStop.put(rootNode.gav, tree);
        for (DependencyNode dependency : rootNode.dependencies) {
            for (DependencyContainerNode dependencyNode : dependencies) {
                if (dependencyNode.gav.group().equals(dependency.group) && dependencyNode.gav.artifact().equals(dependency.artifact)) {
                    DependencyTree child = assembleFullTree(dependencyNode, dependency.classifier, dependency.artifact, dependencies, recursionStop);
                    children.add(child);
                    break;
                }
            }
        }
        return tree;
    }

    private DependencyTree assembleExcludingTree(DependencyContainerNode rootNode, String rootClassifier, String rootType, List<DependencyContainerNode> dependencies, Map<GAV, DependencyTree> recursionStop) {
        DependencyTree root = assembleFullTree(rootNode, rootClassifier, rootType, dependencies, recursionStop);

        Map<DependencyTree, List<DependencyTree>> child2Parents = new HashMap<>();
        Map<GAV, DependencyContainerNode> gav2DependencyContainerNode = new HashMap<>();

        recursionStop.values().forEach((tree) -> {
            for (DependencyTree child : tree.dependencies) {
                child2Parents.compute(child, (key, parents) -> {
                    if (parents == null) {
                        parents = new ArrayList<>();
                    }
                    parents.add(tree);
                    return parents;
                });
            }
        });

        for (DependencyContainerNode containerNode : dependencies) {
            gav2DependencyContainerNode.put(containerNode.gav, containerNode);
        }
        gav2DependencyContainerNode.put(rootNode.gav, rootNode);

        for (DependencyTree treeNode : recursionStop.values()) {
            if (!treeNode.enabled) {
                continue;
            }
            boolean exclude = true;
            boolean grandparentReached = false;
            for (DependencyTree parent : child2Parents.getOrDefault(treeNode, Collections.emptyList())) {
                for (DependencyTree grandparent : child2Parents.getOrDefault(parent, Collections.emptyList())) {
                    grandparentReached = true;
                    for (DependencyNode dep : gav2DependencyContainerNode.get(grandparent.gav).dependencies) {
                        if (dep.artifact.equals(parent.gav.artifact()) && dep.group.equals(parent.gav.group())) {
                            // TODO generally we would want to have the node that is the nearest to the root node
                            //      be the most powerfull one instead of all having the same power over exclusions.
                            //      This means that depth matters - but we would also need to store it.
                            //      Due to us using Depth-First-Search for operations such as Tree assembly
                            //      this is a bit hard to do

                            boolean notExcluding = true;
                            for (Exclusion exclusion : dep.exclusions) {
                                if (exclusion.isExcluding(treeNode.gav)) {
                                    notExcluding = false;
                                    break;
                                }
                            }

                            if (notExcluding) {
                                exclude = false;
                            }
                        }
                    }
                }
            }
            if (exclude && grandparentReached) {
                treeNode.enabled = false;
            }
        }

        return root;
    }

    public void generateDependencyTree(GAV artifact, String classifier, String type, Executor executor, ObjectSink<DependencyTree> sink) {
        getDependencies(artifact, executor).thenAccept((rootNode) -> {
            ConcurrentMap<VersionlessDependency, MavenVersion> neogitationCache = new ConcurrentHashMap<>();
            generateDepenents(Collections.singletonList(rootNode), neogitationCache, executor, new ObjectSink<DependencyContainerNode>() {

                List<DependencyContainerNode> dependencies = new ArrayList<>();

                @Override
                public void onError(Throwable error) {
                    sink.onError(error);
                }

                @Override
                public void nextItem(DependencyContainerNode item) {
                    dependencies.add(item);
                }

                @Override
                public void onComplete() {
                    dependencies.add(rootNode);
                    sink.nextItem(assembleExcludingTree(rootNode, classifier, type, dependencies, new HashMap<>()));
                    sink.onComplete();
                }
            });
        }).exceptionally((t) -> {
            sink.onError(t);
            return null;
        });
    }

    private record VersionlessDependency(String group, String artifact, String classifier, String type) {}

    private void resolveDependencyVersions(Map<VersionlessDependency, VersionRange> from, Map<VersionlessDependency, MavenVersion> to, Executor executor, Runnable doneCallback) {
        if (from.isEmpty()) {
            doneCallback.run();
            return;
        }

        AtomicInteger opened = new AtomicInteger();
        AtomicBoolean finishedLaunching = new AtomicBoolean();
        AtomicBoolean calledCallback = new AtomicBoolean();
        from.forEach((dep, range) -> {
            opened.incrementAndGet();
            getVersions(dep.group, dep.artifact, executor, new ObjectSink<VersionCatalogue>() {

                @Override
                public void onError(Throwable error) {
                    error.printStackTrace();
                    onComplete();
                }

                @Override
                public void nextItem(VersionCatalogue item) {
                    MavenVersion x = to.get(dep);
                    if (x != null && range.containsVersion(x)) {
                        return; // Whatever
                    }
                    MavenVersion selected = null;
                    for (MavenVersion version : item.releaseVersions) {
                        if (range.containsVersion(version) && (selected == null || version.isNewerThan(selected))) {
                            selected = version;
                        }
                    }
                    if (selected == null) {
                        selected = x;
                    }
                    // Go with latest then
                    if (selected == null) {
                        selected = item.releaseVersion;
                    }
                    if (selected == null) {
                        selected = item.latestVersion;
                    }
                    if (selected == null) {
                        throw new NullPointerException("\"selected\" is null. Possible malformed version catalogue?");
                    }
                    to.putIfAbsent(dep, selected);
                }

                @Override
                public void onComplete() {
                    if (opened.decrementAndGet() == 0 && finishedLaunching.get() && calledCallback.compareAndSet(false, true)) {
                        doneCallback.run();
                    }
                }
            });
        });
        if (opened.get() == 0 && calledCallback.compareAndSet(false, true)) {
            calledCallback.set(true);
            doneCallback.run();
            return;
        }
        finishedLaunching.set(true);
        if (opened.get() == 0 && calledCallback.compareAndSet(false, true)) {
            doneCallback.run();
            return;
        }
    }

    private void getDependents(List<DependencyContainerNode> nodes, Map<VersionlessDependency, MavenVersion> negotiated,
            List<DependencyContainerNode> subDeps, Executor executor, Runnable callbackDone) {

        Map<VersionlessDependency, VersionRange> versionRangeIntersections = new HashMap<>();

        for (DependencyContainerNode node : nodes) {
            for (DependencyNode dep : node.dependencies) {
                VersionlessDependency verlessDep = new VersionlessDependency(dep.group, dep.artifact, dep.classifier, dep.type);
                MavenVersion previous = negotiated.get(verlessDep);
                if (previous == null) {
                    VersionRange range = versionRangeIntersections.get(verlessDep);
                    if (range != null) {
                        range = range.intersect(dep.version);
                    } else {
                        range = dep.version;
                    }
                    versionRangeIntersections.put(verlessDep, range);
                }
            }
        }

        Map<VersionlessDependency, MavenVersion> resolvedVersions = new HashMap<>();
        Map<VersionlessDependency, VersionRange> unresolvedVersions = new HashMap<>();

        for (Map.Entry<VersionlessDependency, VersionRange> entry : versionRangeIntersections.entrySet()) {
            MavenVersion version = entry.getValue().getRecommended();
            if (version == null) {
                unresolvedVersions.put(entry.getKey(), entry.getValue());
            } else {
                resolvedVersions.put(entry.getKey(), version);
            }
        }

        resolveDependencyVersions(unresolvedVersions, resolvedVersions, executor, () -> {
            resolvedVersions.keySet().forEach(unresolvedVersions::remove);
            unresolvedVersions.forEach((dep, range) -> {
                System.err.println("Cannot resolve a concrete version for dependency " + dep + " within the version range \"" + range + "\"");
            });

            negotiated.putAll(resolvedVersions);

            Set<GAV> resolveGAVs = ConcurrentHashMap.newKeySet(resolvedVersions.size());

            for (Map.Entry<VersionlessDependency, MavenVersion> dependency : resolvedVersions.entrySet()) {
                resolveGAVs.add(new GAV(dependency.getKey().group, dependency.getKey().artifact, dependency.getValue()));
            }

            if (resolveGAVs.isEmpty()) {
                callbackDone.run();
                return;
            }

            AtomicBoolean concurrencyLock = new AtomicBoolean(); // This object simply exists to avoid incredibly rare issues concerning concurrency
            for (GAV dependencyGAV : resolveGAVs) {
                if (dependencyGAV.group().startsWith("${pom.groupId}")) {
                    throw new IllegalStateException("This is nonsense! " + dependencyGAV);
                }
                CompletableFuture<DependencyContainerNode> subdependencyFuture = getDependencies(dependencyGAV, executor);
                subdependencyFuture.exceptionally((t) -> {
                    t.printStackTrace();
                    if (resolveGAVs.remove(dependencyGAV) && resolveGAVs.isEmpty() && concurrencyLock.compareAndSet(false, true)) {
                        callbackDone.run();
                    }
                    return null;
                });
                subdependencyFuture.thenAccept((item) -> {
                    if (item == null) {
                        throw new NullPointerException("Argument \"item\" is null!");
                    }
                    if (concurrencyLock.get()) {
                        throw new IllegalStateException("Already completed!");
                    }
                    subDeps.add(item);
                    if (resolveGAVs.remove(dependencyGAV) && resolveGAVs.isEmpty() && concurrencyLock.compareAndSet(false, true)) {
                        callbackDone.run();
                    }
                });
            }
        });
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

    private CompletableFuture<DependencyContainerNode> getDependencies(GAV gav, Executor executor) {
        DependencyContainerNode cached = depdenencyCache.get(gav);
        if (cached != null) {
            return CompletableFuture.completedFuture(cached);
        }
        CompletableFuture<DependencyContainerNode> future = new CompletableFuture<>();
        CompletableFuture<RepositoryAttachedValue<Path>> rootParentPom = download(gav, null, "pom", executor);
        rootParentPom.exceptionally((t) -> {
            future.completeExceptionally(t);
            return null;
        });
        rootParentPom.thenAccept((RepositoryAttachedValue<Path> pathRAV) -> {

            DependencyContainerNode newCachedNode = depdenencyCache.get(gav);
            if (newCachedNode != null) {
                future.complete(newCachedNode);
                return;
            }

            try {
                SAXReader reader = new SAXReader();
                reader.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
                reader.setFeature("http://xml.org/sax/features/external-general-entities", false);
                reader.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
                Document xmlDoc;
                try (InputStream is = Files.newInputStream(pathRAV.getValue())) {
                    xmlDoc = reader.read(is);
                }
                Element project = xmlDoc.getRootElement();
                project.normalize();

                Element parent = project.element("parent");
                if (parent == null) {
                    DependencyContainerNode dependencyContainerNode = new DependencyContainerNode(gav);
                    fillInDependencies(gav, executor, xmlDoc, Collections.emptyList(), dependencyContainerNode).thenAccept((ignoreVoid) -> {
                        DependencyContainerNode c2 = depdenencyCache.putIfAbsent(gav, dependencyContainerNode);
                        if (c2 == null) {
                            future.complete(dependencyContainerNode);
                        } else {
                            future.complete(c2);
                        }
                    });
                } else {
                    List<Map.Entry<GAV, Document>> parents = new CopyOnWriteArrayList<>();
                    downloadParentPoms(parent, executor, new ObjectSink<Map.Entry<GAV, Document>>() {

                        @Override
                        public void onError(Throwable error) {
                            future.completeExceptionally(error);
                        }

                        @Override
                        public void nextItem(Map.Entry<GAV, Document> item) {
                            parents.add(item);
                        }

                        @Override
                        public void onComplete() {
                            DependencyContainerNode dependencyContainerNode = new DependencyContainerNode(gav);
                            fillInDependencies(gav, executor, xmlDoc, parents, dependencyContainerNode).thenAccept((ignoreVoid) -> {
                                DependencyContainerNode c2 = depdenencyCache.putIfAbsent(gav, dependencyContainerNode);
                                if (c2 == null) {
                                    future.complete(dependencyContainerNode);
                                } else {
                                    future.complete(c2);
                                }
                            });
                        }
                    });
                }
            } catch (Throwable t) {
                future.completeExceptionally(t);
            }
        });
        return future;
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
            throw new IllegalStateException("Unable to find snapshot version");
        }), () -> {
            return downloadSimple(gav, classifier, extension, executor);
        });
    }
}
