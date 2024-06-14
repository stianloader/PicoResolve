package org.stianloader.picoresolve.version;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

// Based on https://maven.apache.org/enforcer/enforcer-rules/versionRanges.html
public class VersionRange {

    // Basically an interval where the other bound is infinity.
    private static class Edge implements VersionSet {
        private final MavenVersion edgeVersion;
        private final EdgeType type;

        public Edge(MavenVersion edgeVersion, EdgeType type) {
            this.edgeVersion = edgeVersion;
            this.type = type;
        }

        @Override
        public boolean contains(MavenVersion version) {
            if (this.type == EdgeType.UP_TO) {
                return !version.isNewerThan(this.edgeVersion);
            } else if (this.type == EdgeType.UNDER) {
                return this.edgeVersion.isNewerThan(version);
            } else if (this.type == EdgeType.NOT_UNDER) {
                return !this.edgeVersion.isNewerThan(version);
            } else {
                // Type is EdgeType.ABOVE
                return version.isNewerThan(this.edgeVersion);
            }
        }

        @Override
        public boolean equals(Object obj) {
            if (obj instanceof Edge) {
                Edge other = (Edge) obj;
                return other.edgeVersion.equals(this.edgeVersion) && other.type.equals(this.type);
            }

            return false;
        }

        @Override
        public int hashCode() {
            return Objects.hash(this.edgeVersion, this.type);
        }

        @Override
        public String toString() {
            if (this.type == EdgeType.UP_TO) {
                return "(," + this.edgeVersion + ']';
            } else if (this.type == EdgeType.UNDER) {
                return "(," + this.edgeVersion + ')';
            } else if (this.type == EdgeType.NOT_UNDER) {
                return "[" + this.edgeVersion + ",)";
            } else {
                // Type is EdgeType.ABOVE
                return "(" + this.edgeVersion + ",)";
            }
        }
    }
    private enum EdgeType {
        UP_TO,     // x <= 1.0 - (,1.0]
        UNDER,     // x <  1.0 - (,1.0)
        NOT_UNDER, // x >= 1.0 - [1.0,)
        ABOVE;     // x >  1.0 - (1.0,)
    }

    private static class Interval implements VersionSet {
        // lower bound is the oldest accepted version (for a closed interval that is)
        private final MavenVersion lowerBound;
        // upper bound is the newest accepted version (for a closed interval that is)
        private final MavenVersion upperBound;
        private final IntervalType type;

        public Interval(MavenVersion lowerBound, MavenVersion upperBound, IntervalType type) {
            this.lowerBound = lowerBound;
            this.upperBound = upperBound;
            this.type = type;
        }

        @Override
        public boolean contains(MavenVersion version) {
            if (this.type == IntervalType.CLOSED) {
                return !version.isNewerThan(this.upperBound) && !this.lowerBound.isNewerThan(version);
            } else if (this.type == IntervalType.UPPER_OPEN) {
                return this.upperBound.isNewerThan(version) && !this.lowerBound.isNewerThan(version);
            } else if (this.type == IntervalType.LOWER_OPEN) {
                return !version.isNewerThan(this.upperBound) && version.isNewerThan(this.lowerBound);
            } else {
                // type is IntervalType.BOTH_OPEN
                return version.isNewerThan(this.lowerBound) && this.upperBound.isNewerThan(version);
            }
        }

        @Override
        public boolean equals(Object obj) {
            if (obj instanceof Interval) {
                Interval other = (Interval) obj;
                return other.lowerBound.equals(this.lowerBound)
                        && other.upperBound.equals(this.upperBound)
                        && other.type.equals(this.type);
            }
            return false;
        }

        @Override
        public int hashCode() {
            return Objects.hash(this.lowerBound, this.upperBound, this.type);
        }

        @Override
        public String toString() {
            if (this.type == IntervalType.CLOSED) {
                return '[' + this.lowerBound.toString() + ',' + this.upperBound + ']';
            } else if (this.type == IntervalType.UPPER_OPEN) {
                return '[' + this.lowerBound.toString() + ',' + this.upperBound + ')';
            } else if (this.type == IntervalType.LOWER_OPEN) {
                return '(' + this.lowerBound.toString() + ',' + this.upperBound + ']';
            } else {
                // type is IntervalType.BOTH_OPEN
                return '(' + this.lowerBound.toString() + ',' + this.upperBound + ')';
            }
        }
    }

    private enum IntervalType {
        BOTH_OPEN,   // a <  x <  b - ]a;b[ - (a,b)
        CLOSED,      // a <= x <= b - [a;b] - [a,b]
        UPPER_OPEN,  // a <= x <  b - [a;b[ - [a,b)
        LOWER_OPEN;  // a <  x <= b - ]a;b] - (a,b]
    }

    private static class PinnedVersion implements VersionSet {
        private final MavenVersion version;

        public PinnedVersion(MavenVersion version) {
            this.version = version;
        }

        @Override
        public boolean contains(MavenVersion version) {
            return !this.version.isNewerThan(version) && !version.isNewerThan(this.version);
        }

        @Override
        public String toString() {
            return '[' + this.version.toString() + ']';
        }
    }

    private static interface VersionSet {
        boolean contains(MavenVersion version);
    }

    /**
     * Sentinel value marking a version range which allows any value - corresponding to the string ','.
     *
     * <p>Note that for the maven artifact resolver, this concept does not exist.
     * This concept does exist under picoresolve though as a convenience method to always select the newest
     * version. Unlike {@link #RELEASE}, {@link #FREE_RANGE} works by selecting the newest version from
     * the maven-metadata.xml while {@link #RELEASE} works by selecting the advertised release version from
     * the maven-metadata.xml file.
     *
     * @implNote The difference between {@link #FREE_RANGE} and {@link #RELEASE} is wholly based on
     * identity. Behaviour between the two instances only differs for the {@link #selectFrom(Collection, MavenVersion)}
     * method.
     */
    @NotNull
    public static final VersionRange FREE_RANGE = new VersionRange(Collections.emptyList(), Collections.emptyList());

    /**
     * Sentinel value marking a version range which accepts the latest versions defined by the
     * 'release' field of the A-level metadata.xml file. This generally corresponds to the newest
     * non-snapshjot version published to the repositories.
     *
     * <p>This field corresponds to the strings 'RELEASE' as well as '[RELEASE]' as the value
     * of this field will be returned when trying to parse these values using {@link VersionRange#parse(String)}.
     * Usage of the 'RELEASE' string is not permitted in version ranges - if this occurs,
     * picoresolve will treat it as a {@link MavenVersion} version string - that is a string literal with
     * no meaning, but other resolver implementations might throw an error in that case.
     *
     * <p>When using {@link VersionRange#parse(String)} the case of the characters matters - that is the literal 'RELEASE'
     * corresponds to this field, where as the literal 'release' would correspond to a {@link MavenVersion}
     * without any special meaning.
     *
     * <p>Note: The usage of this field is discouraged as it follows against the principle of reproducibility
     * and stability, which for back-end software is generally not favourable.
     *
     * @implNote The difference between {@link #FREE_RANGE} and {@link #RELEASE} is wholly based on
     * identity. Where as behaviour between the two instances only differs for the
     * {@link #selectFrom(Collection, MavenVersion)} method.
     *
     * <p>Further, {@link #RELEASE} like {@link #FREE_RANGE} accepts any values for a call to
     * {@link #containsVersion(MavenVersion)}.
     */
    @NotNull
    public static final VersionRange RELEASE = new VersionRange(Collections.emptyList(), Collections.emptyList());

    @NotNull
    public static VersionRange parse(@NotNull String string) {
        if (string.equals(",")) {
            return VersionRange.FREE_RANGE;
        } else if (string.equals("RELEASE") || string.equals("[RELEASE]")) {
            return VersionRange.RELEASE;
        }

        List<@NotNull VersionSet> sets = new ArrayList<>();
        List<@NotNull MavenVersion> recommendedVersions = new ArrayList<>();

        int[] codepoints = string.codePoints().toArray();

        List<String> tokens = new ArrayList<>();

        boolean parsingRange = false;
        int lastDelimiter = -1;
        for (int i = 0; i < codepoints.length; i++) {
            int codepoint = codepoints[i];
            if (codepoint == '(' || codepoint == '[') {
                parsingRange = true;
            } else if (codepoint == ')' || codepoint == ']') {
                parsingRange = false;
            } else if (codepoint == ',' && !parsingRange) {
                tokens.add(string.substring(lastDelimiter + 1, i));
                lastDelimiter = i;
            }
        }

        if ((lastDelimiter + 1) != codepoints.length) {
            tokens.add(string.substring(lastDelimiter + 1, codepoints.length));
        }

        for (String token : tokens) {
            if (token.codePointAt(0) != '[' && token.codePointAt(0) != '(') {
                // Parse as recommended version
                recommendedVersions.add(MavenVersion.parse(token));
            } else {
                boolean edge = token.codePointAt(1) == ',' || token.codePointAt(token.length() - 2) == ',';
                boolean closedLeft = token.codePointAt(0) == '[';
                boolean closedRight = token.codePointAt(token.length() - 1) == ']';
                if (edge) {
                    boolean edgeLeft = token.codePointAt(1) == ',';
                    MavenVersion version;
                    EdgeType type;
                    if (edgeLeft) {
                        version = MavenVersion.parse(token.substring(2, token.length() - 1));
                        if (closedRight) {
                            type = EdgeType.UP_TO;
                        } else {
                            type = EdgeType.UNDER;
                        }
                    } else {
                        version = MavenVersion.parse(token.substring(1, token.length() - 2));
                        if (closedLeft) {
                            type = EdgeType.NOT_UNDER;
                        } else {
                            type = EdgeType.ABOVE;
                        }
                    }
                    sets.add(new Edge(version, type));
                } else {
                    int seperatorPos = token.indexOf(',');
                    if (seperatorPos == -1) {
                        if (closedLeft && closedRight) {
                            // Most likely [version]
                            sets.add(new PinnedVersion(MavenVersion.parse(token.substring(1, token.length() - 1))));
                        } else {
                            // Most likely a single edge version string such as '(0.5', '0.4)' or similar.
                            // That being said, these version strings are not supported by maven from the looks of it
                            // so they are left open for now.
                            // The proper way to define these strings is using '(0.5,]', '(,0.4)' or similar
                            throw new AssertionError(token + "---" + string);
                        }
                    } else {
                        MavenVersion left = MavenVersion.parse(token.substring(1, seperatorPos));
                        MavenVersion right = MavenVersion.parse(token.substring(seperatorPos + 1, token.length() - 1));
                        IntervalType type;
                        if (closedLeft) {
                            if (closedRight) {
                                type = IntervalType.CLOSED;
                            } else {
                                type = IntervalType.UPPER_OPEN;
                            }
                        } else {
                            if (closedRight) {
                                type = IntervalType.LOWER_OPEN;
                            } else {
                                type = IntervalType.BOTH_OPEN;
                            }
                        }
                        sets.add(new Interval(left, right, type));
                    }
                }
            }
        }
        return new VersionRange(sets, recommendedVersions);
    }

    @NotNull
    private final List<@NotNull MavenVersion> recommendedVersions;

    @NotNull
    private final List<@NotNull VersionSet> versionSets;

    private VersionRange(@NotNull List<@NotNull VersionSet> sets, @NotNull List<@NotNull MavenVersion> recommended) {
        this.recommendedVersions = Collections.unmodifiableList(new ArrayList<>(recommended));
        this.versionSets = Collections.unmodifiableList(new ArrayList<>(sets));
    }

    public boolean containsVersion(MavenVersion version) {
        if (this.versionSets.isEmpty()) {
            // Maven treats version recommendations as implicit pins.
            // Not doing so would mean that arbitrary version strings would automatically
            // use the newest version if it isn't defined already - which is pure nonsense.
            for (MavenVersion recommended : this.recommendedVersions) {
                if (recommended.compareTo(version) == 0) {
                    return true;
                }
            }
            return false;
        }

        for (VersionSet set : this.versionSets) {
            if (!set.contains(version)) {
                return false;
            }
        }
        return true;
    }

    /**
     * Obtains the newest ("highest") recommended version that is within the version range.
     * If there are no recommended versions or if none of these versions match the constraints set up
     * by the version range, null is returned.
     *
     * <p>Note: In the absence of any explicit range rules, as is the case when a {@link VersionRange}
     * is a plain version, then the recommended version will implicitly be treated as "pins". That is,
     * they are the only allowed versions.
     *
     * @return The highest recommended version that lies within the bounds of the version range.
     */
    @Nullable
    public MavenVersion getRecommended() {
        MavenVersion newest = null;
        for (MavenVersion version : this.recommendedVersions) {
            if (this.containsVersion(version) && (newest == null || version.isNewerThan(newest))) {
                newest = version;
            }
        }
        return newest;
    }

    public List<@NotNull MavenVersion> getRecommendedVersions() {
        return this.recommendedVersions;
    }

    @NotNull
    public VersionRange intersect(@NotNull VersionRange version) {
        if (this == VersionRange.FREE_RANGE) {
            return version;
        } else if (version == VersionRange.FREE_RANGE) {
            return this;
        }

        // TODO Honestly, this isn't a correct representation of how it should be done given that the string
        // could probably more be seen as an expandable expression rather than anything else.
        // Perhaps it should be treated as a boolean flag? I'd need to think about this for a while.
        // Thankfully though I believe that we will rather rarely encounter RELEASE versions.
        if (this == VersionRange.RELEASE) {
            return version;
        } else if (version == VersionRange.RELEASE) {
            return this;
        }

        List<@NotNull VersionSet> sets = new ArrayList<>(this.versionSets);
        List<@NotNull MavenVersion> recommended = new ArrayList<>(this.recommendedVersions);
        sets.addAll(version.versionSets);
        recommended.addAll(version.recommendedVersions);
        return new VersionRange(sets, recommended);
    }

    @Nullable
    public MavenVersion selectFrom(@Nullable Collection<@NotNull MavenVersion> knownAvailable, @Nullable MavenVersion releaseVersion) {
        if (this == VersionRange.RELEASE) {
            return releaseVersion;
        }

        MavenVersion candidateVersion = null;
        for (MavenVersion version : this.getRecommendedVersions()) {
            if ((candidateVersion == null || version.isNewerThan(candidateVersion)) && this.containsVersion(version)) {
                candidateVersion = version;
            }
        }

        if (candidateVersion != null) {
            return candidateVersion;
        }

        if (knownAvailable != null) {
            for (MavenVersion known : knownAvailable) {
                if ((candidateVersion == null || known.isNewerThan(candidateVersion)) && this.containsVersion(known)) {
                    candidateVersion = known;
                }
            }

            if (candidateVersion != null) {
                return candidateVersion;
            }
        }

        return null;
    }

    @Override
    public String toString() {
        StringBuilder builder = new StringBuilder();
        for (VersionSet set : this.versionSets) {
            builder.append(set.toString());
            builder.append(',');
        }
        if (this.recommendedVersions.isEmpty()) {
            if (this.versionSets.isEmpty()) {
                return ",";
            }
            builder.setLength(builder.length() - 1);
            return builder.toString();
        }
        for (MavenVersion recommended : this.recommendedVersions) {
            builder.append(recommended.toString());
            builder.append(',');
        }
        builder.setLength(builder.length() - 1);
        return builder.toString();
    }
}
