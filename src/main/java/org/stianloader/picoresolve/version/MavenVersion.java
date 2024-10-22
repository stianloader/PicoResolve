package org.stianloader.picoresolve.version;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;

import org.jetbrains.annotations.NotNull;
import org.stianloader.picoresolve.internal.ConfusedResolverException;
import org.stianloader.picoresolve.internal.JavaInterop;

/**
 * Object that represents a version that maven can understand and compare.
 * Based on <a href="https://maven.apache.org/pom.html#version-order-specification">https://maven.apache.org/pom.html#version-order-specification</a>
 */
public class MavenVersion implements Comparable<MavenVersion> {

    private final List<MavenVersionPart> parts;
    private final String origin;

    private MavenVersion(String origin, List<MavenVersionPart> parts) {
        this.parts = Collections.unmodifiableList(new ArrayList<>(parts));
        this.origin = origin;
    }


    @NotNull
    public static MavenVersion parse(@NotNull String string) {
        List<String> tokens = MavenVersion.purgeTokens(MavenVersion.splitTokens(string));
        if (tokens.isEmpty()) {
            return new MavenVersion(string, Arrays.asList());
        }
        List<MavenVersionPart> parts = new ArrayList<>();
        int prefix = '-';
        for (Iterator<String> it = tokens.iterator(); it.hasNext();) {
            String token = it.next();
            try {
                parts.add(new NumericVersionPart(prefix, Integer.valueOf(token)));
            } catch (NumberFormatException nfe) {
                switch (token) {
                case "alpha":
                case "beta":
                case "milestone":
                case "rc":
                case "cr":
                case "snapshot":
                case "final":
                case "ga":
                case "sp":
                    parts.add(new PrereleaseVersionPart(prefix, token));
                    break;
                default:
                    parts.add(new QualifierVersionPart(prefix, token));
                    break;
                }
            }
            if (it.hasNext()) {
                String x = it.next();
                if (x.length() != 1) {
                    throw new ConfusedResolverException("Got string \"" + x + "\" from the iterator");
                }
                prefix = x.codePointAt(0);
            }
        }
        return new MavenVersion(string, parts);
    }

    private static List<String> purgeTokens(List<String> tokens) {
        // "Then, starting from the end of the version, the trailing "null" values (0, "", "final", "ga") are trimmed."
        // "This process is repeated at each remaining hyphen from end to start."

        boolean trimMode = true;
        boolean trimFromEnd = true;

        for (int i = tokens.size() - 1; i >= 0; i--) {
            String token = tokens.get(i);
            if (trimMode && (token.equals("0") || token.equals("final") || token.equals("ga") || token.equals(".") || (trimFromEnd && token.equals("-")))) {
                tokens.remove(i);
            } else if (!token.equals("-")) {
                trimMode = false;
            }
            if (token.equals("-")) {
                if (trimMode && !trimFromEnd) {
                    tokens.add(i + 1, "0");
                }
                trimMode = true;
                trimFromEnd = false;
            }
        }

        if (trimMode && !trimFromEnd) {
            tokens.add(0, "0");
        }

        return tokens;
    }

    private static List<String> splitTokens(String versionString) {
        // Undocumented nonsense: The version string is always lowercased
        versionString = versionString.toLowerCase(Locale.ROOT);
        // "The Maven coordinate is split in tokens between dots ('.'), hyphens ('-') and transitions between digits and characters.
        // "The separator is recorded and will have effect on the order."
        // To fulfil these rules, the returned array will have semantics such as {TOKEN, SEPERATOR, TOKEN, SEPERATOR, TOKEN}
        // "1-" will as such return {"1", "-"}
        // "1-final" will return {"1", "-", "final"}
        // "1.0.2c0" will return {"1", ".", "0", ".", "2", "-", "c", "-", "0"} as a transition between chars and digits are equivalent to a hypen
        // "1.-" will return {"1", ".", "0", "-"}
        // "." will return {"0", "."}
        // This method does not deal with trimming "0", "final", "", "ga", however
        // does deal with replacing shorthands.
        // This means that "1.2b0" will return {"1", ".", "2", "-", "beta", "-", "0"}

        int[] codepoints = versionString.codePoints().toArray();
        List<String> tokens = new ArrayList<>();
        int lastSeperator = -1;
        boolean wasDigit = false;

        for (int i = 0; i < codepoints.length; i++) {
            int codepoint = codepoints[i];
            if (codepoint == '-' || codepoint == '.') {
                int len = i - (lastSeperator + 1);
                if (len == 0) {
                    tokens.add("0");
                } else {
                    tokens.add(versionString.substring(lastSeperator + 1, i));
                }
                tokens.add(JavaInterop.codepointToString(codepoint));
                lastSeperator = i;
            } else if ((lastSeperator + 1) != i) {
                if (!wasDigit) {
                    if (Character.isDigit(codepoint)) {
                        String token = versionString.substring(lastSeperator + 1, i);
                        if (token.length() == 1) {
                            if (token.equals("a")) {
                                token = "alpha";
                            } else if (token.equals("b")) {
                                token = "beta";
                            } else if (token.equals("m")) {
                                token = "milestone";
                            }
                        }
                        tokens.add(token);
                        tokens.add("-");
                        lastSeperator = i - 1;
                    }
                } else if (!Character.isDigit(codepoint)) {
                    tokens.add(versionString.substring(lastSeperator + 1, i));
                    tokens.add("-");
                    lastSeperator = i - 1;
                }
            }
            wasDigit = Character.isDigit(codepoint);
        }

        if ((lastSeperator + 1) != versionString.length()) {
            tokens.add(versionString.substring(lastSeperator + 1, versionString.length()));
        }

        return tokens;
    }

    @Override
    public int compareTo(MavenVersion o) {
        int maxIndex = Math.min(parts.size(), o.parts.size());
        for (int i = 0; i < maxIndex; i++) {
            int cmp = parts.get(i).compareTo(o.parts.get(i));
            if (cmp != 0) {
                return cmp;
            }
        }
        if (parts.size() == o.parts.size()) {
            return 0;
        }
        if (parts.size() < o.parts.size()) {
            MavenVersionPart part = o.parts.get(parts.size());
            if (part instanceof NumericVersionPart || part instanceof QualifierVersionPart) {
                return -1;
            }
            return -part.compareTo(new PrereleaseVersionPart(part.getPrefixCodepoint(), ""));
        }
        if (parts.size() > o.parts.size()) {
            MavenVersionPart part = parts.get(o.parts.size());
            if (part instanceof NumericVersionPart || part instanceof QualifierVersionPart) {
                return 1;
            }
            return part.compareTo(new PrereleaseVersionPart(part.getPrefixCodepoint(), ""));
        }
        return 0;
    }

    @Override
    public String toString() {
        StringBuilder builder = new StringBuilder();
        builder.append(origin);
        builder.append(" [Interpreted as ");
        StringBuilder builder2 = new StringBuilder();
        for (MavenVersionPart part : parts) {
            builder2.append(part.toString());
        }
        if (builder2.length() != 0) {
            builder2.deleteCharAt(0);
            builder.append(builder2);
        }
        builder.append(']');
        return builder.toString();
    }

    public boolean isNewerThan(MavenVersion older) {
        return this.compareTo(older) > 0;
    }

    @Override
    public int hashCode() {
        StringBuilder builder2 = new StringBuilder();
        for (MavenVersionPart part : parts) {
            builder2.append(part.toString());
        }
        return builder2.toString().hashCode();
    }

    @Override
    public boolean equals(Object obj) {
        if (obj instanceof MavenVersion) {
            MavenVersion other = (MavenVersion) obj;
            if (other.parts.size() != parts.size()) {
                return false;
            }
            for (int i = 0; i < other.parts.size(); i++) {
                if (!other.parts.get(i).stringifyContent().equals(this.parts.get(i).stringifyContent())) {
                    return false;
                }
            }
            return true;
        }
        return false;
    }

    public String getOriginText() {
        return origin;
    }
}
