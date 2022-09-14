package de.geolykt.mavenresolver;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.ListIterator;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
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
import de.geolykt.mavenresolver.misc.ChildElementIterable;
import de.geolykt.mavenresolver.misc.ConfusedResolverException;
import de.geolykt.mavenresolver.misc.ObjectSink;
import de.geolykt.mavenresolver.misc.SinkMultiplexer;
import de.geolykt.mavenresolver.version.MavenVersion;
import de.geolykt.mavenresolver.version.VersionCatalogue;
import de.geolykt.mavenresolver.version.VersionCatalogue.SnapshotVersion;
import de.geolykt.mavenresolver.version.VersionRange;

public class MavenResolver {

    // TODO test tree resolving capabilities with https://repo1.maven.org/maven2/org/alfasoftware/astra/2.1.1/astra-2.1.1.pom
    // TODO cache VersionCatalogue objects
    // TODO cache poms
    private final List<MavenRepository> repositories = new CopyOnWriteArrayList<>();
    private final ConcurrentMap<GAV, DependencyContainerNode> depdenencyCache = new ConcurrentHashMap<>();

    public MavenResolver() {
        this(null);
    }

    public MavenResolver(Collection<MavenRepository> repos) {
        if (repos != null) {
            addRepositories(repos);
        }
    }

    public MavenResolver addRepositories(Collection<MavenRepository> repos) {
        repos.forEach(this::addRepository);
        return this;
    }

    public MavenResolver addRepository(MavenRepository repo) {
        if (!repositories.contains(repo)) {
            repositories.add(repo);
        }
        return this;
    }

    public MavenResolver addRepositories(MavenRepository... repos) {
        for (MavenRepository mr : repos) {
            addRepository(mr);
        }
        return this;
    }

    public void getResource(String path, Executor executor, ObjectSink<MavenResource> sink) {
        SinkMultiplexer<MavenResource> multiplexer = new SinkMultiplexer<>(new ObjectSink<>() {

            private final AtomicBoolean hadItem = new AtomicBoolean();
            private final AtomicBoolean closed = new AtomicBoolean();

            @Override
            public void onError(Throwable error) {
                if (closed.compareAndExchange(false, true)) {
                    return;
                }
                sink.onError(error);
            }

            @Override
            public void nextItem(MavenResource item) {
                hadItem.set(true);
                if (closed.get()) {
                    return;
                }
                try {
                    sink.nextItem(item);
                } catch (Exception e) {
                    sink.onError(e);
                }
            }

            @Override
            public void onComplete() {
                if (closed.compareAndExchange(false, true)) {
                    return;
                }
                if (hadItem.get()) {
                    sink.onComplete();
                } else {
                    IOException e = new IOException("No repository declared resource \"" + path + "\"");
                    e.fillInStackTrace();
                    sink.onError(e);
                }
            }
        });
        boolean empty = true;
        for (MavenRepository repository : repositories) {
            empty = false;
            repository.getResource(path, executor, multiplexer.newDelegateParent());
        }
        if (empty) {
            throw new ConfusedResolverException("Resolver has no repository it can fetch resources from");
        }
    }

    public void getVersions(String groupId, String artifactId, Executor executor, ObjectSink<VersionCatalogue> sink) {
        String mavenMetadataPath = groupId.replace('.', '/') + '/' + artifactId + "/maven-metadata.xml";

        getResource(mavenMetadataPath, executor, new ObjectSink<MavenResource>() {

            private final AtomicBoolean errored = new AtomicBoolean();

            @Override
            public void onError(Throwable error) {
                if (errored.getAndSet(true)) {
                    return;
                }
                errored.set(true);
                sink.onError(error);
            }

            @Override
            public void nextItem(MavenResource item) {
                if (errored.get()) {
                    return;
                }
                VersionCatalogue catalogue;
                try (InputStream is = Files.newInputStream(item.getPath())) {
                    catalogue = new VersionCatalogue(is);
                } catch (Exception e) {
                    onError(e);
                    return;
                }
                sink.nextItem(catalogue);
            }

            @Override
            public void onComplete() {
                if (errored.get()) {
                    return;
                }
                sink.onComplete();
            }
        });
    }

    public void download(GAV gav, String classifier, String extension, Executor executor, ObjectSink<MavenResource> sink) {
        if (gav.version().getOriginText().toLowerCase(Locale.ROOT).equals("-snapshot")) {
            downloadSnapshot(gav, classifier, extension, executor, sink);
        } else {
            downloadSimple(gav, classifier, extension, executor, new ObjectSink<MavenResource>() {

                private boolean hadItem = false;

                @Override
                public void onError(Throwable error) {
                    if (hadItem) {
                        sink.onError(error);
                    } else {
                        downloadSnapshot(gav, classifier, extension, executor, sink);
                    }
                }

                @Override
                public void nextItem(MavenResource item) {
                    hadItem = true;
                    sink.nextItem(item);
                }

                @Override
                public void onComplete() {
                    if (hadItem) {
                        sink.onComplete();
                    } else {
                        downloadSnapshot(gav, classifier, extension, executor, sink);
                    }
                }
            });
        }
    }

    private void downloadParentPoms(Element parentElement, Executor executor, ObjectSink<Document> sink) {
        if (parentElement == null) {
            sink.onComplete();
            return;
        }
        String group = parentElement.elementText("groupId");
        String artifactId = parentElement.elementText("artifactId");
        String version = parentElement.elementText("version");

        download(new GAV(group, artifactId, MavenVersion.parse(version)), null, "pom", executor, new ObjectSink<MavenResource>() {

            private volatile boolean hadElements = false;

            @Override
            public void onError(Throwable error) {
                if (!hadElements) {
                    sink.onComplete();
                }
            }

            @Override
            public void nextItem(MavenResource item) {
                hadElements = true;
                try {
                    SAXReader reader = new SAXReader();
                    reader.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
                    reader.setFeature("http://xml.org/sax/features/external-general-entities", false);
                    reader.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
                    Document xmlDoc;
                    try (InputStream is = Files.newInputStream(item.getPath())) {
                        xmlDoc = reader.read(is);
                    }
                    Element project = xmlDoc.getRootElement();
                    project.normalize();

                    sink.nextItem(xmlDoc);

                    Element parent = project.element("parent");

                    if (parent == null) {
                        sink.onComplete();
                        return;
                    } else {
                        downloadParentPoms(parent, executor, sink);
                    }
                } catch (Exception e) {
                    sink.onError(e);
                }
            }

            @Override
            public void onComplete() {
                if (!hadElements) {
                    sink.onComplete();
                }
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

    private void computePlaceholders(GAV gav, Document pom, List<Document> parentPom, Map<String, String> out) {
        out.put("project.version", gav.version().getOriginText());
        out.put("project.groupId", gav.group());
        out.put("project.artifactId", gav.artifact());
        if (!parentPom.isEmpty()) {
            for (ListIterator<Document> lit = parentPom.listIterator(parentPom.size()); lit.hasPrevious();) {
                Element properties = lit.previous().getRootElement().element("properties");
                if (properties == null) {
                    continue;
                }
                for (Element prop : new ChildElementIterable(properties)) {
                    out.put(prop.getName(), prop.getText());
                }
            }
        }
        Element properties = pom.getRootElement().element("properties");
        if (properties != null) {
            for (Element prop : new ChildElementIterable(properties)) {
                out.put(prop.getName(), prop.getText());
            }
        }
    }

    private void readDependencies(Element dependencyBlock, Map<String, String> placeholders, DependencyContainerNode node) {
        for (Element dependency : new ChildElementIterable(dependencyBlock)) {
            // FIXME the dependency management block is a thing. AND it is apparently inherited from parent POMs.
            //       not too much of an issue, but must be implemented eventually nonetheless

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
                throw new IllegalStateException();
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

    private void fillInDependencies(GAV gav, Document pom, List<Document> parentPoms, DependencyContainerNode node) {
        Map<String, String> placeholders = new HashMap<>();
        computePlaceholders(gav, pom, parentPoms, placeholders);
        Element project = pom.getRootElement();
        Element dependencies = project.element("dependencies");
        if (dependencies != null) {
            readDependencies(dependencies, placeholders, node);
        }
    }

    private void generateDepenents(List<DependencyContainerNode> nodes, ConcurrentMap<VersionlessDependency, MavenVersion> negotiatedCache, Executor executor, ObjectSink<DependencyContainerNode> sink) {
        if (nodes.isEmpty()) {
            throw new IllegalArgumentException("Argument \"nodes\" is an empty list. What is the point of this method call?");
        }
        List<DependencyContainerNode> subnodes = new ArrayList<>();
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
        getDependencies(artifact, executor, new ObjectSink<DependencyContainerNode>() {

            private DependencyContainerNode rootNode;

            @Override
            public void onError(Throwable error) {
                sink.onError(error);
            }

            @Override
            public void nextItem(DependencyContainerNode item) {
                rootNode = item;
            }

            @Override
            public void onComplete() {
                if (rootNode == null) {
                    sink.onError(new IOException("Unable to find POM for " + artifact).fillInStackTrace());
                } else {
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
                }
            }
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
                getDependencies(dependencyGAV, executor, new ObjectSink<DependencyContainerNode>() {

                    @Override
                    public void onError(Throwable error) {
                        error.printStackTrace();
                        if (resolveGAVs.remove(dependencyGAV) && resolveGAVs.isEmpty() && concurrencyLock.compareAndSet(false, true)) {
                            callbackDone.run();
                        }
                    }

                    @Override
                    public void nextItem(DependencyContainerNode item) {
                        if (item == null) {
                            throw new NullPointerException("Argument \"item\" is null!");
                        }
                        subDeps.add(item);
                    }

                    @Override
                    public void onComplete() {
                        if (resolveGAVs.remove(dependencyGAV) && resolveGAVs.isEmpty() && concurrencyLock.compareAndSet(false, true)) {
                            callbackDone.run();
                        }
                    }
                });
            }
        });
    }

    private void getDependencies(GAV gav, Executor executor, ObjectSink<DependencyContainerNode> sink) {
        DependencyContainerNode cached = depdenencyCache.get(gav);
        if (cached != null) {
            sink.nextItem(cached);
            sink.onComplete();
            return;
        }
        download(gav, null, "pom", executor, new ObjectSink<MavenResource>() {

            private volatile boolean processedElement = false;

            @Override
            public void onError(Throwable error) {
                if (!processedElement) {
                    sink.onError(error);
                }
            }

            @Override
            public void nextItem(MavenResource item) {
                processedElement = true;
                DependencyContainerNode cached = depdenencyCache.get(gav);
                if (cached != null) {
                    sink.nextItem(cached);
                    sink.onComplete();
                    return;
                }

                try {
                    SAXReader reader = new SAXReader();
                    reader.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
                    reader.setFeature("http://xml.org/sax/features/external-general-entities", false);
                    reader.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
                    Document xmlDoc;
                    try (InputStream is = Files.newInputStream(item.getPath())) {
                        xmlDoc = reader.read(is);
                    }
                    Element project = xmlDoc.getRootElement();
                    project.normalize();

                    Element parent = project.element("parent");
                    if (parent == null) {
                        DependencyContainerNode dependencyContainerNode = new DependencyContainerNode(gav);
                        fillInDependencies(gav, xmlDoc, Collections.emptyList(), dependencyContainerNode);
                        cached = depdenencyCache.putIfAbsent(gav, dependencyContainerNode);
                        if (cached != null) {
                            dependencyContainerNode = cached;
                        }
                        sink.nextItem(dependencyContainerNode);
                        sink.onComplete();
                        return;
                    } else {
                        List<Document> parents = new CopyOnWriteArrayList<>();
                        downloadParentPoms(parent, executor, new ObjectSink<Document>() {

                            @Override
                            public void onError(Throwable error) {
                                sink.onError(error);
                            }

                            @Override
                            public void nextItem(Document item) {
                                parents.add(item);
                            }

                            @Override
                            public void onComplete() {
                                DependencyContainerNode dependencyContainerNode = new DependencyContainerNode(gav);
                                fillInDependencies(gav, xmlDoc, parents, dependencyContainerNode);
                                DependencyContainerNode cached = depdenencyCache.putIfAbsent(gav, dependencyContainerNode);
                                if (cached != null) {
                                    dependencyContainerNode = cached;
                                }
                                sink.nextItem(dependencyContainerNode);
                                sink.onComplete();
                                return;
                            }
                        });
                    }
                } catch (Exception e) {
                    sink.onError(e);
                }
            }

            @Override
            public void onComplete() {
                if (processedElement) {
                    return;
                }
                sink.onComplete();
            }
        });
    }

    private void downloadSimple(GAV gav, String classifier, String extension, Executor executor, ObjectSink<MavenResource> sink) {
        String basePath = gav.group().replace('.', '/') + '/' + gav.artifact() + '/' + gav.version().getOriginText() + '/';
        String path = basePath + gav.artifact() + '-' + gav.version().getOriginText();
        if (classifier != null) {
            path += '-' + classifier;
        }
        path += '.' + extension;
        getResource(path, executor, sink);
    }

    private void downloadSnapshot(GAV gav, String classifier, String extension, Executor executor, ObjectSink<MavenResource> sink) {
        String basePath = gav.group().replace('.', '/') + '/' + gav.artifact() + '/' + gav.version().getOriginText() + '/';
        getResource(basePath + "maven-metadata.xml", executor, new ObjectSink<MavenResource>() {

            private AtomicBoolean recievedResource = new AtomicBoolean();

            @Override
            public void onError(Throwable error) {
                if (!recievedResource.get()) {
                    // Well then let's download it in a "stupid" manner
                    downloadSimple(gav, classifier, extension, executor, sink);
                }
            }

            @Override
            public void nextItem(MavenResource item) {
                if (recievedResource.get()) {
                    return;
                }
                try {
                    VersionCatalogue catalogue = new VersionCatalogue(Files.newInputStream(item.getPath()));
                    for (SnapshotVersion snapshot : catalogue.snapshotVersions) {
                        if (!snapshot.extension().equals(extension)) {
                            continue;
                        }
                        if (snapshot.classifier() != null && !snapshot.classifier().equals(classifier)) {
                            continue;
                        }
                        if (snapshot.classifier() == null && classifier != null) {
                            continue;
                        }
                        if (recievedResource.compareAndExchange(false, true)) {
                            return;
                        }
                        String path = basePath + gav.artifact() + '-' + snapshot.version();
                        if (classifier != null) {
                            path += '-' + classifier;
                        }
                        path += '.' + extension;
                        getResource(path, executor, sink);
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                    onError(e);
                    return;
                }
            }

            @Override
            public void onComplete() {
                if (!recievedResource.get()) {
                    // Well then let's download it in a "stupid" manner
                    downloadSimple(gav, classifier, extension, executor, sink);
                }
            }
        });
    }
}
