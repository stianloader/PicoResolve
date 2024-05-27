package org.stianloader.picoresolve.internal.meta;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import org.jetbrains.annotations.NotNull;
import org.stianloader.picoresolve.internal.ConfusedResolverException;
import org.stianloader.picoresolve.internal.XMLUtil;
import org.stianloader.picoresolve.internal.XMLUtil.ChildElementIterable;
import org.stianloader.picoresolve.version.MavenVersion;
import org.stianloader.picoresolve.version.VersionRange;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.xml.sax.SAXException;

public class VersionCatalogue {

    public static class SnapshotVersion {
        private final String classifier;
        private final String extension;
        private final String lastUpdated;
        private final String version;

        public SnapshotVersion(String extension, String classifier, String version, String lastUpdated) {
            this.extension = extension;
            this.classifier = classifier;
            this.version = version;
            this.lastUpdated = lastUpdated;
        }

        public String classifier() {
            return this.classifier;
        }

        @Override
        public boolean equals(Object obj) {
            if (obj instanceof SnapshotVersion) {
                SnapshotVersion other = (SnapshotVersion) obj;
                return this.extension.equals(other.extension)
                        && this.classifier.equals(other.classifier)
                        && this.version.equals(other.version)
                        && this.lastUpdated.equals(other.lastUpdated);
            }
            return false;
        }

        public String extension() {
            return this.extension;
        }

        @Override
        public int hashCode() {
            return Objects.hash(this.extension, this.classifier, this.version, this.lastUpdated);
        }

        public String lastUpdated() {
            return this.lastUpdated;
        }

        public String version() {
            return this.version;
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

    public String fallbackSnapshotVersion;
    public String lastUpdated;
    public int lastUpdateDay;
    public int lastUpdateHour;
    public int lastUpdateMinute;
    public int lastUpdateMonth;
    public int lastUpdateSecond;
    public int lastUpdateYear;
    public MavenVersion latestVersion;
    public boolean localCopy;
    public MavenVersion releaseVersion;
    @NotNull
    public final List<@NotNull MavenVersion> releaseVersions = new ArrayList<>();

    @NotNull
    public final List<@NotNull SnapshotVersion> snapshotVersions = new ArrayList<>();

    private VersionCatalogue() {
        // No-arguments constructor needed for the #merge method
    }

    public VersionCatalogue(InputStream is) throws SAXException, IOException, ParserConfigurationException {
        Document xmlDoc;
        {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            xmlDoc = factory.newDocumentBuilder().parse(is);
        }

        if (xmlDoc == null) {
            throw new NullPointerException("xmlDoc is null");
        }

        Element metadata = xmlDoc.getDocumentElement();
        metadata.normalize();

        Element versioning = XMLUtil.reqElement(metadata, "versioning");
        Element versions = null;
        Element snapshotVersions = null;

        for (Element element : new ChildElementIterable(versioning)) {
            switch (element.getTagName().toLowerCase(Locale.ROOT)) {
            case "versions":
                versions = element;
                break;
            case "release":
                this.releaseVersion = MavenVersion.parse(Objects.requireNonNull(element.getTextContent()));
                break;
            case "latest":
                this.latestVersion = MavenVersion.parse(Objects.requireNonNull(element.getTextContent()));
                break;
            case "lastupdated":
                this.lastUpdated = element.getTextContent();

                if (this.lastUpdated.length() != 14) {
                    throw new ConfusedResolverException("Last updated string \"" + this.lastUpdated + "\" is not following an implemented standard.");
                }

                this.lastUpdateYear = Integer.parseInt(this.lastUpdated.substring(0, 4));
                this.lastUpdateMonth = Integer.parseInt(this.lastUpdated.substring(4, 6));
                this.lastUpdateDay = Integer.parseInt(this.lastUpdated.substring(6, 8));
                this.lastUpdateHour = Integer.parseInt(this.lastUpdated.substring(8, 10));
                this.lastUpdateMinute = Integer.parseInt(this.lastUpdated.substring(10, 12));
                this.lastUpdateSecond = Integer.parseInt(this.lastUpdated.substring(12, 14));
                break;
            case "snapshotversions":
                snapshotVersions = element;
                break;
            case "version":
                // Encountered in https://repo1.maven.org/maven2/org/eclipse/core/commands/maven-metadata.xml
                if (this.latestVersion != null) {
                    throw new IllegalStateException();
                }
                this.latestVersion = MavenVersion.parse(Objects.requireNonNull(element.getTextContent()));
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
        Element snapshots = XMLUtil.optElement(versioning, "snapshot");
        Element versionElement = XMLUtil.optElement(metadata, "version");

        if ((versions == null && snapshotVersions == null)
                && (snapshots == null || versionElement == null)) {
            // Even this amount of data is too little for our resolver at the moment, so we will have to throw the towel here
            throw new ConfusedResolverException("Data did not contain a valid maven-metadata.xml file that lists the versions of an artifact.");
        }

        if (snapshots != null && versionElement != null) {
            Element timestamp = XMLUtil.optElement(snapshots, "timestamp");
            Element buildNr = XMLUtil.optElement(snapshots, "buildNumber");
            Element localCopy = XMLUtil.optElement(snapshots, "localCopy");
            if ((timestamp == null || buildNr == null) && localCopy == null) {
                throw new ConfusedResolverException("Too little data remaining to be able to build up a fallback snapshot version: " + metadata.toString());
            }
            if (localCopy != null) {
                this.localCopy = "true".equalsIgnoreCase(localCopy.getTextContent());
            }
            if (timestamp != null && buildNr != null) {
                int index = versionElement.getTextContent().toLowerCase().lastIndexOf("-snapshot");
                if (index == -1) {
                    this.fallbackSnapshotVersion = versionElement.getTextContent() + "-" + timestamp.getTextContent() + "-" + buildNr.getTextContent();
                } else {
                    this.fallbackSnapshotVersion = versionElement.getTextContent().substring(0, index) + "-" + timestamp.getTextContent() + "-" + buildNr.getTextContent();
                }
            }
        }

        if (versions != null) {
            for (Element element : new ChildElementIterable(versions)) {
                if (element.getTagName().equalsIgnoreCase("version")) {
                    this.releaseVersions.add(MavenVersion.parse(Objects.requireNonNull(element.getTextContent())));
                }
            }
        }

        if (snapshotVersions != null) {
            for (Element element : new ChildElementIterable(snapshotVersions)) {
                if (element.getTagName().equalsIgnoreCase("snapshotVersion")) {
                    String extension = XMLUtil.elementText(element, "extension");
                    String lastUpdated = XMLUtil.elementText(element, "updated");
                    String version = XMLUtil.elementText(element, "value");
                    String classifier = XMLUtil.elementText(element, "classifier");
                    this.snapshotVersions.add(new SnapshotVersion(extension, classifier, version, lastUpdated));
                }
            }
        }
    }

    @NotNull
    public MavenVersion selectVersion(@NotNull VersionRange range) {
        List<@NotNull MavenVersion> recommended = new ArrayList<>(range.getRecommendedVersions());
        recommended.sort((v1, v2) -> v2.compareTo(v1));
        for (MavenVersion version : recommended) {
            if (this.releaseVersions.contains(version)) {
                return version;
            }
        }

        MavenVersion newestRelease = null;
        for (MavenVersion v : this.releaseVersions) {
            if (range.containsVersion(v) && (newestRelease == null || v.isNewerThan(newestRelease))) {
                newestRelease = v;
            }
        }

        if (newestRelease != null) {
            return newestRelease;
        }

        MavenVersion v = range.getRecommended();
        if (v == null) {
            // This seemingly strange fallback mainly exists in cases where maven-metadata files are not present or incomplete
            throw new IllegalStateException("No recommended version is defined in version range and no release version matches the constraints of the version range.");
        }
        return v;
    }
}
