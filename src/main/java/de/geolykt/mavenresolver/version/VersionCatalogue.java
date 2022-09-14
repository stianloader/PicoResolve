package de.geolykt.mavenresolver.version;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import org.dom4j.Document;
import org.dom4j.DocumentException;
import org.dom4j.Element;
import org.dom4j.io.SAXReader;
import org.xml.sax.SAXException;

import de.geolykt.mavenresolver.misc.ChildElementIterable;
import de.geolykt.mavenresolver.misc.ConfusedResolverException;

public class VersionCatalogue {

    public static record SnapshotVersion(String extension, String classifier, String version, String lastUpdated) {
    }

    public final List<MavenVersion> releaseVersions = new ArrayList<>();
    public final List<SnapshotVersion> snapshotVersions = new ArrayList<>();
    public MavenVersion releaseVersion;
    public MavenVersion latestVersion;
    public String lastUpdated;
    public int lastUpdateYear;
    public int lastUpdateMonth;
    public int lastUpdateDay;
    public int lastUpdateHour;
    public int lastUpdateMinute;
    public int lastUpdateSecond;

    public VersionCatalogue(InputStream is) throws SAXException, DocumentException, IOException {
        SAXReader reader = new SAXReader();
        reader.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
        reader.setFeature("http://xml.org/sax/features/external-general-entities", false);
        reader.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
        DocumentException caught = null;
        Document xmlDoc = null;
        try {
            xmlDoc = reader.read(is);
        } catch (DocumentException t) {
            caught = t;
        } finally {
            try {
                is.close();
            } catch (Throwable t) {
                if (caught == null) {
                    throw t;
                }
                t.addSuppressed(caught);
                throw t;
            }
            if (caught != null) {
                throw caught;
            }
        }

        if (xmlDoc == null) {
            throw new NullPointerException("xmlDoc is null");
        }

        Element metadata = xmlDoc.getRootElement();
        metadata.normalize();

        Element versioning = metadata.element("versioning");
        Element versions = null;
        Element snapshotVersions = null;

        for (Element element : new ChildElementIterable(versioning)) {
            switch (element.getName().toLowerCase(Locale.ROOT)) {
            case "versions":
                versions = element;
                break;
            case "release":
                releaseVersion = MavenVersion.parse(element.getText());
                break;
            case "latest":
                latestVersion = MavenVersion.parse(element.getText());
                break;
            case "lastupdated":
                lastUpdated = element.getText();

                if (lastUpdated.length() != 14) {
                    throw new ConfusedResolverException("Last updated string \"" + lastUpdated + "\" is not following an implemented standard.");
                }

                lastUpdateYear = Integer.parseInt(lastUpdated.substring(0, 4));
                lastUpdateMonth = Integer.parseInt(lastUpdated.substring(4, 6));
                lastUpdateDay = Integer.parseInt(lastUpdated.substring(6, 8));
                lastUpdateHour = Integer.parseInt(lastUpdated.substring(8, 10));
                lastUpdateMinute = Integer.parseInt(lastUpdated.substring(10, 12));
                lastUpdateSecond = Integer.parseInt(lastUpdated.substring(12, 14));
                break;
            case "snapshotversions":
                snapshotVersions = element;
                break;
            case "version":
                // Encountered in https://repo1.maven.org/maven2/org/eclipse/core/commands/maven-metadata.xml
                if (latestVersion != null) {
                    throw new IllegalStateException();
                }
                latestVersion = MavenVersion.parse(element.getText());
                break;
            }
        }

        if (versions == null && snapshotVersions == null) {
            throw new ConfusedResolverException("Data did not contain a valid maven-metadata.xml file that lists the versions of an artifact.");
        }

        if (versions != null) {
            for (Element element : new ChildElementIterable(versions)) {
                if (element.getName().equalsIgnoreCase("version")) {
                    this.releaseVersions.add(MavenVersion.parse(element.getText()));
                }
            }
        }

        if (snapshotVersions != null) {
            for (Element element : new ChildElementIterable(snapshotVersions)) {
                if (element.getName().equalsIgnoreCase("snapshotVersion")) {
                    String extension = element.elementText("extension");
                    String lastUpdated = element.elementText("updated");
                    String version = element.elementText("value");
                    String classifier = element.elementText("classifier");
                    this.snapshotVersions.add(new SnapshotVersion(extension, classifier, version, lastUpdated));
                }
            }
        }
    }
}
