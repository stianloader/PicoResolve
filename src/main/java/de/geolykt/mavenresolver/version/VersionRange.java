package de.geolykt.mavenresolver.version;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

// Based on https://maven.apache.org/enforcer/enforcer-rules/versionRanges.html
public class VersionRange {

    private final List<MavenVersion> recommendedVersions;
    private final List<VersionSet> versionSets;

    private VersionRange(List<VersionSet> sets, List<MavenVersion> recommended) {
        this.recommendedVersions = Collections.unmodifiableList(new ArrayList<>(recommended));
        this.versionSets = Collections.unmodifiableList(new ArrayList<>(sets));
    }

    public static VersionRange parse(String string) {
        if (string.length() == 1 && string.codePointAt(0) == ',') {
            return new VersionRange(Collections.emptyList(), Collections.emptyList());
        }
        List<VersionSet> sets = new ArrayList<>();
        List<MavenVersion> recommendedVersions = new ArrayList<>();

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

    public List<MavenVersion> getRecommendedVersions() {
        return recommendedVersions;
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
    private static record Interval(MavenVersion lowerBound, MavenVersion upperBound, IntervalType type) implements VersionSet {
        @Override
        public boolean contains(MavenVersion version) {
            if (type == IntervalType.CLOSED) {
                return !version.isNewerThan(upperBound) && !lowerBound.isNewerThan(version);
            } else if (type == IntervalType.UPPER_OPEN) {
                return upperBound.isNewerThan(version) && !lowerBound.isNewerThan(version);
            } else if (type == IntervalType.LOWER_OPEN) {
                return !version.isNewerThan(upperBound) && version.isNewerThan(lowerBound);
            } else {
                // type is IntervalType.BOTH_OPEN
                return version.isNewerThan(lowerBound) && !upperBound.isNewerThan(version);
            }
        }

        @Override
        public String toString() {
            if (type == IntervalType.CLOSED) {
                return '[' + lowerBound.toString() + ',' + upperBound + ']';
            } else if (type == IntervalType.UPPER_OPEN) {
                return '[' + lowerBound.toString() + ',' + upperBound + ')';
            } else if (type == IntervalType.LOWER_OPEN) {
                return '(' + lowerBound.toString() + ',' + upperBound + ']';
            } else {
                // type is IntervalType.BOTH_OPEN
                return '(' + lowerBound.toString() + ',' + upperBound + ')';
            }
        }
    }

    private enum EdgeType {
        UP_TO,     // x <= 1.0 - (,1.0]
        UNDER,     // x <  1.0 - (,1.0)
        NOT_UNDER, // x >= 1.0 - [1.0,)
        ABOVE;     // x >  1.0 - (1.0,)
    }

    // Basically an interval where the other bound is infinity.
    private static record Edge(MavenVersion edgeVersion, EdgeType type) implements VersionSet {
        @Override
        public boolean contains(MavenVersion version) {
            if (type == EdgeType.UP_TO) {
                return !version.isNewerThan(edgeVersion);
            } else if (type == EdgeType.UNDER) {
                return edgeVersion.isNewerThan(version);
            } else if (type == EdgeType.NOT_UNDER) {
                return !edgeVersion.isNewerThan(version);
            } else {
                // Type is EdgeType.ABOVE
                return version.isNewerThan(edgeVersion);
            }
        }

        @Override
        public String toString() {
            if (type == EdgeType.UP_TO) {
                return "(," + edgeVersion + ']';
            } else if (type == EdgeType.UNDER) {
                return "(," + edgeVersion + ')';
            } else if (type == EdgeType.NOT_UNDER) {
                return "[" + edgeVersion + ",)";
            } else {
                // Type is EdgeType.ABOVE
                return "(" + edgeVersion + ",)";
            }
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

    public VersionRange intersect(VersionRange version) {
        List<VersionSet> sets = new ArrayList<>(this.versionSets);
        List<MavenVersion> recommended = new ArrayList<>(this.recommendedVersions);
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
