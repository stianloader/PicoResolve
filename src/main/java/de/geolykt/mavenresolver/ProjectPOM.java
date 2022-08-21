package de.geolykt.mavenresolver;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

import org.dom4j.Document;
import org.dom4j.Element;
import org.dom4j.Node;
import org.dom4j.Text;

import de.geolykt.mavenresolver.version.VersionRange;

@Deprecated
public class ProjectPOM {

    public record Dependency(String group, String artifact, VersionRange version, Scope scope,
            boolean optional, String type, String classifier) {

        private static Dependency fromElement(Element dep, Map<String, String> placeholders) {
            String group = dep.elementText("groupId");
            String artifact = dep.elementText("artifactId");
            String version = dep.elementText("version");
            if (version == null) {
                // TODO test with https://repo1.maven.org/maven2/org/alfasoftware/astra/2.1.1/astra-2.1.1.pom
                throw new IllegalStateException("version is null");
            }
            Scope scope = Scope.fromElement(dep.element("scope"));
            VersionRange versionRange = VersionRange.parse(version);
            boolean optional = "true".equalsIgnoreCase(dep.elementText("optional"));
            String classifier = dep.elementText("classifier");
            String type = dep.elementText("type");

            return new Dependency(group, artifact, versionRange, scope, optional, type, classifier);
        }
    }

    public final GAV gav;
    public final Set<Dependency> dependencies;

    private ProjectPOM(GAV gav, Set<Dependency> dependencies) {
        this.gav = gav;
        this.dependencies = dependencies;
    }

    private static void replaceProjectPlaceholders(Element element) {
        for (int i = 0, size = element.nodeCount(); i < size; i++) {
            Node node = element.node(i);
            if (node instanceof Element elem) {
                replaceProjectPlaceholders(elem);
            } else if (node instanceof Text text) {
                text.setText(text.getText().replace("${pom.", "${").replace("${project.", "${"));
            }
        }
    }

    public static ProjectPOM parse(Document document, GAV expectedGAV) {
        Element project = document.getRootElement();
        replaceProjectPlaceholders(project);

        Map<String, String> placeholders = new HashMap<>();
        Element properties = project.element("properties");
        if (properties != null) {
            for (Iterator<Element> it = properties.elementIterator(); it.hasNext();) {
                Element prop = it.next();
                placeholders.put(prop.getName(), prop.getText());
            }
        }

        Set<Dependency> dependencies = new HashSet<>();

        Element dependenciesElement = project.element("dependencies");
        if (dependenciesElement != null) {
            for (Iterator<Element> it = dependenciesElement.elementIterator(); it.hasNext();) {
                Dependency dep = Dependency.fromElement(it.next(), placeholders);
                if (dep != null) {
                    dependencies.add(dep);
                }
            }
        }

        Element depManagement = project.element("dependencyManagement");
        if (depManagement != null) {
            Element depManagementDependencies = depManagement.element("dependencies");
            if (depManagementDependencies != null) {
                for (Iterator<Element> it = depManagementDependencies.elementIterator(); it.hasNext();) {
                    Dependency dep = Dependency.fromElement(it.next(), placeholders);
                    if (dep != null) {
                        dependencies.add(dep);
                    }
                }
            }
        }

        return new ProjectPOM(expectedGAV, dependencies);
    }
}
