package de.geolykt.picoresolve.internal.meta;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

import org.dom4j.Document;
import org.dom4j.DocumentException;
import org.dom4j.Element;
import org.dom4j.io.SAXReader;
import org.xml.sax.SAXException;

import de.geolykt.picoresolve.internal.ChildElementIterable;
import de.geolykt.picoresolve.internal.ConfusedResolverException;
import de.geolykt.picoresolve.version.MavenVersion;
import de.geolykt.picoresolve.version.VersionRange;

public class VersionCatalogue {

    public static record SnapshotVersion(String extension, String classifier, String version, String lastUpdated) {
    }

    public final List<MavenVersion> releaseVersions = new ArrayList<>();
    public final List<SnapshotVersion> snapshotVersions = new ArrayList<>();
    public MavenVersion releaseVersion;
    public MavenVersion latestVersion;
    public String lastUpdated;
    public String fallbackSnapshotVersion;
    public boolean localCopy;
    public int lastUpdateYear;
    public int lastUpdateMonth;
    public int lastUpdateDay;
    public int lastUpdateHour;
    public int lastUpdateMinute;
    public int lastUpdateSecond;

    private VersionCatalogue() {
        // No-arguments constructor needed for the #merge method
    }

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

        // This one is interesting.
        // It seems as if the resolver does not need to process this edge case scenario when it is in isolation.
        // However, for some reason it seems like once the resolver makes use of a shared maven local cache
        // (with eclipse's m2e and the standard maven-artifact-resolver) something abbreviates the
        // cached version catalogues and the snapshotVersions element is not stored in them.
        // We can however restore the minimum data needed to proceed via the "snapshot" element, while this may
        // introduce additional operational risks the idea behind this workaround is that whatever introduces
        // this anomaly has nothing else to work with, so our resolve shouldn't need the extra information either.

        // Of course we could just invalidate the cache and request the file again, but this would introduce
        // further latency as well as complexity in the library (as this error state would most likely need to be handled
        // by the caller, as the needed repository references are unavailable within this constructor).
        Element snapshots = versioning.element("snapshot");
        Element versionElement = metadata.element("version");

        if ((versions == null && snapshotVersions == null)
                && (snapshots == null || versionElement == null)) {
            // Even this amount of data is too little for our resolver at the moment, so we will have to throw the towel here
            throw new ConfusedResolverException("Data did not contain a valid maven-metadata.xml file that lists the versions of an artifact.");
        }

        if (snapshots != null && versionElement != null) {
            Element timestamp = snapshots.element("timestamp");
            Element buildNr = snapshots.element("buildNumber");
            Element localCopy = snapshots.element("localCopy");
            if ((timestamp == null || buildNr == null) && localCopy == null) {
                throw new ConfusedResolverException("Too little data remaining to be able to build up a fallback snapshot version: " + metadata.asXML());
            }
            if (localCopy != null) {
                this.localCopy = "true".equalsIgnoreCase(localCopy.getText());
            }
            if (timestamp != null && buildNr != null) {
                int index = versionElement.getText().toLowerCase().lastIndexOf("-snapshot");
                if (index == -1) {
                    this.fallbackSnapshotVersion = versionElement.getText() + "-" + timestamp.getText() + "-" + buildNr.getText();
                } else {
                    this.fallbackSnapshotVersion = versionElement.getText().substring(0, index) + "-" + timestamp.getText() + "-" + buildNr.getText();
                }
            }
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

    public static VersionCatalogue merge(Iterable<VersionCatalogue> sources) {
        VersionCatalogue merged = new VersionCatalogue();
        for (VersionCatalogue source : sources) {
            for (MavenVersion ver : source.releaseVersions) {
                if (!merged.releaseVersions.contains(ver)) {
                    merged.releaseVersions.add(ver);
                }
            }
            for (SnapshotVersion ver : source.snapshotVersions) {
                SnapshotVersion conflict = null;
                for (SnapshotVersion old : merged.snapshotVersions) {
                    if (Objects.equals(old.classifier, ver.classifier)
                            && old.extension.equals(ver.extension)) {
                        conflict = old;
                        break;
                    }
                }
                if (conflict == null) {
                    merged.snapshotVersions.add(ver);
                } else {
                    if (conflict.lastUpdated.compareTo(ver.lastUpdated) < 0) {
                        // Conflict was updated at an earlier date
                        merged.snapshotVersions.remove(conflict);
                        merged.snapshotVersions.add(ver);
                        continue;
                    }
                }
            }
            if (source.lastUpdateYear > merged.lastUpdateYear) {
                merged.lastUpdateYear = source.lastUpdateYear;
                merged.lastUpdateMonth = source.lastUpdateMonth;
                merged.lastUpdateDay = source.lastUpdateDay;
                merged.lastUpdateHour = source.lastUpdateHour;
                merged.lastUpdateMinute = source.lastUpdateMinute;
                merged.lastUpdateSecond = source.lastUpdateSecond;
            } else if (source.lastUpdateYear < merged.lastUpdateYear) {
                // Do nothing
            } else if (source.lastUpdateMonth > merged.lastUpdateMonth) {
                merged.lastUpdateMonth = source.lastUpdateMonth;
                merged.lastUpdateDay = source.lastUpdateDay;
                merged.lastUpdateHour = source.lastUpdateHour;
                merged.lastUpdateMinute = source.lastUpdateMinute;
                merged.lastUpdateSecond = source.lastUpdateSecond;
            } else if (source.lastUpdateMonth < merged.lastUpdateMonth) {
                // Do nothing
            } else if (source.lastUpdateDay > merged.lastUpdateDay) {
                merged.lastUpdateDay = source.lastUpdateDay;
                merged.lastUpdateHour = source.lastUpdateHour;
                merged.lastUpdateMinute = source.lastUpdateMinute;
                merged.lastUpdateSecond = source.lastUpdateSecond;
            } else if (source.lastUpdateDay < merged.lastUpdateDay) {
                // Do nothing
            } else if (source.lastUpdateHour > merged.lastUpdateHour) {
                merged.lastUpdateHour = source.lastUpdateHour;
                merged.lastUpdateMinute = source.lastUpdateMinute;
                merged.lastUpdateSecond = source.lastUpdateSecond;
            } else if (source.lastUpdateHour < merged.lastUpdateHour) {
                // Do nothing
            } else if (source.lastUpdateMinute > merged.lastUpdateMinute) {
                merged.lastUpdateMinute = source.lastUpdateMinute;
                merged.lastUpdateSecond = source.lastUpdateSecond;
            } else if (source.lastUpdateMinute < merged.lastUpdateMinute) {
                // Do nothing
            } else if (source.lastUpdateSecond > merged.lastUpdateSecond) {
                merged.lastUpdateSecond = source.lastUpdateSecond;
            } else if (source.lastUpdateSecond < merged.lastUpdateSecond) {
                // Do nothing
            }

            if (merged.latestVersion == null) {
                merged.latestVersion = source.latestVersion;
            } else if (source.latestVersion != null && source.latestVersion.isNewerThan(merged.latestVersion)) {
                merged.latestVersion = source.latestVersion;
            }

            if (merged.releaseVersion == null) {
                merged.releaseVersion = source.releaseVersion;
            } else if (source.releaseVersion != null && source.releaseVersion.isNewerThan(merged.releaseVersion)) {
                merged.releaseVersion = source.releaseVersion;
            }

            if (merged.fallbackSnapshotVersion != null) {
                merged.fallbackSnapshotVersion = source.fallbackSnapshotVersion;
            }

            merged.localCopy |= source.localCopy;
        }

        merged.lastUpdated = String.format("%04d%02d%02d%02d%02d%02d", merged.lastUpdateYear, merged.lastUpdateMonth,
                merged.lastUpdateDay, merged.lastUpdateHour, merged.lastUpdateMinute, merged.lastUpdateSecond);
        return merged;
    }

    public MavenVersion selectVersion(VersionRange range) {
        List<MavenVersion> recommended = new ArrayList<>(range.getRecommendedVersions());
        recommended.sort((v1, v2) -> v2.compareTo(v1));
        for (MavenVersion version : recommended) {
            if (this.releaseVersions.contains(version)) {
                return version;
            }
        }
        for (MavenVersion v : this.releaseVersions) {
            if (range.containsVersion(v)) {
                return v;
            }
        }
        return range.getRecommended();
    }
}
