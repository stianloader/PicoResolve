package org.stianloader.picoresolve.version;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

// Based on https://maven.apache.org/enforcer/enforcer-rules/versionRanges.html
public class VersionRange {

    @NotNull
    private final List<@NotNull MavenVersion> recommendedVersions;
    @NotNull
    private final List<@NotNull VersionSet> versionSets;

    @NotNull
    public static final VersionRange FREE_RANGE = VersionRange.parse(",");

    private VersionRange(@NotNull List<@NotNull VersionSet> sets, @NotNull List<@NotNull MavenVersion> recommended) {
        this.recommendedVersions = Collections.unmodifiableList(new ArrayList<>(recommended));
        this.versionSets = Collections.unmodifiableList(new ArrayList<>(sets));
    }

    @NotNull
    public static VersionRange parse(@NotNull String string) {
        if (string.length() == 1 && string.codePointAt(0) == ',') {
            return new VersionRange(Collections.emptyList(), Collections.emptyList());
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

    public boolean containsVersion(MavenVersion version) {
        for (VersionSet set : versionSets) {
            if (!set.contains(version)) {
                return false;
            }
        }
        return true;
    }

    public List<@NotNull MavenVersion> getRecommendedVersions() {
        return this.recommendedVersions;
    }

    private static interface VersionSet {
        boolean contains(MavenVersion version);
    }

    private enum IntervalType {
        BOTH_OPEN,   // a <  x <  b - ]a;b[ - (a,b)
        CLOSED,      // a <= x <= b - [a;b] - [a,b]
        UPPER_OPEN,  // a <= x <  b - [a;b[ - [a,b)
        LOWER_OPEN;  // a <  x <= b - ]a;b] - (a,b]
    }

    // lower bound is the oldest accepted version (for a closed interval that is)
    // upper bound is the newest accepted version (for a closed interval that is)
    private static class Interval implements VersionSet {
        private final MavenVersion lowerBound;
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
                return this.upperBound.isNewerThan(version) && version.isNewerThan(this.lowerBound);
            } else if (this.type == IntervalType.LOWER_OPEN) {
                return !version.isNewerThan(this.upperBound) && version.isNewerThan(this.lowerBound);
            } else {
                // type is IntervalType.BOTH_OPEN
                return version.isNewerThan(this.lowerBound) && !this.upperBound.isNewerThan(version);
            }
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

        @Override
        public int hashCode() {
            return Objects.hash(this.lowerBound, this.upperBound, this.type);
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
    }

    private enum EdgeType {
        UP_TO,     // x <= 1.0 - (,1.0]
        UNDER,     // x <  1.0 - (,1.0)
        NOT_UNDER, // x >= 1.0 - [1.0,)
        ABOVE;     // x >  1.0 - (1.0,)
    }

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
            return '[' + version.toString() + ']';
        }
    }

    @Override
    public String toString() {
        StringBuilder builder = new StringBuilder();
        for (VersionSet set : versionSets) {
            builder.append(set.toString());
            builder.append(',');
        }
        if (recommendedVersions.isEmpty()) {
            if (versionSets.isEmpty()) {
                return ",";
            }
            builder.setLength(builder.length() - 1);
            return builder.toString();
        }
        for (MavenVersion recommended : recommendedVersions) {
            builder.append(recommended.toString());
            builder.append(',');
        }
        builder.setLength(builder.length() - 1);
        return builder.toString();
    }

    @NotNull
    public VersionRange intersect(@NotNull VersionRange version) {
        if (this == FREE_RANGE) {
            return version;
        } else if (version == FREE_RANGE) {
            return this;
        }
        List<@NotNull VersionSet> sets = new ArrayList<>(this.versionSets);
        List<@NotNull MavenVersion> recommended = new ArrayList<>(this.recommendedVersions);
        sets.addAll(version.versionSets);
        recommended.addAll(version.recommendedVersions);
        return new VersionRange(sets, recommended);
    }

    /**
     * Obtains the newest ("highest") recommended version that is within the version range.
     * If there are no recommended versions or if none of these versions match the constraints set up
     * by the version range, null is returned.
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
}
