package org.stianloader.picoresolve.version;

import java.util.HashMap;
import java.util.Map;

import org.stianloader.picoresolve.internal.ConfusedResolverException;

final class PrereleaseVersionPart implements MavenVersionPart {

    private static final Map<String, Integer> QUALIFIER_VALUES = new HashMap<>();
    private int prefixCodepoint;
    private String qualifier;

    public PrereleaseVersionPart(int prefixCodepoint, String qualifier) {
        this.prefixCodepoint = prefixCodepoint;
        this.qualifier = qualifier;
    }

    @Override
    public int compareTo(MavenVersionPart o) {
        if (o instanceof NumericVersionPart) {
            return -1; // Qualifiers are always "lesser" than numbers
        }
        if (o instanceof QualifierVersionPart) {
            return -1; // Pre-release version parts are always less recent than qualifiers
        }
        if (o instanceof PrereleaseVersionPart other) {
            if (other.prefixCodepoint != this.prefixCodepoint) {
                if (this.prefixCodepoint == '.' && other.prefixCodepoint == '-') {
                    // '.' is less than '-' for qualifiers
                    return -1;
                } else if (this.prefixCodepoint == '-' && other.prefixCodepoint == '.') {
                    // '-' is ,more than '.' for qualifiers
                    return 1;
                } else {
                    throw new ConfusedResolverException("Prefix codepoint confusion");
                }
            }
            return QUALIFIER_VALUES.get(qualifier).compareTo(QUALIFIER_VALUES.get(other.qualifier));
        }
        throw new IllegalArgumentException("Cannot compare a prerelease version part to a " + o.getClass().descriptorString());
    }

    @Override
    public int getPrefixCodepoint() {
        return prefixCodepoint;
    }

    @Override
    public String stringifyContent() {
        return qualifier;
    }

    @Override
    public String toString() {
        return Character.toString(getPrefixCodepoint()) + stringifyContent();
    }

    static {
        QUALIFIER_VALUES.put("alpha", 0);
        QUALIFIER_VALUES.put("beta", 1);
        QUALIFIER_VALUES.put("milestone", 2);
        QUALIFIER_VALUES.put("rc", 3);
        QUALIFIER_VALUES.put("cr", 3);
        QUALIFIER_VALUES.put("snapshot", 4);
        QUALIFIER_VALUES.put("", 5);
        QUALIFIER_VALUES.put("final", 5);
        QUALIFIER_VALUES.put("ga", 5);
        QUALIFIER_VALUES.put("sp", 6);
    }
}
