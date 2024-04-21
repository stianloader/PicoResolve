package org.stianloader.picoresolve.version;

import org.stianloader.picoresolve.internal.ConfusedResolverException;

final class QualifierVersionPart implements MavenVersionPart {

    private int prefixCodepoint;
    private String qualifier;

    public QualifierVersionPart(int prefix, String qualifier) {
        this.prefixCodepoint = prefix;
        this.qualifier = qualifier;
    }

    @Override
    public int compareTo(MavenVersionPart o) {
        if (o instanceof NumericVersionPart) {
            return -1; // Qualifiers are always less "recent" than numbers
        }
        if (o instanceof PrereleaseVersionPart) {
            return 1; // Pre-release version parts are always less recent than qualifiers
        }
        if (o instanceof QualifierVersionPart other) {
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
            return qualifier.compareTo(other.qualifier);
        }
        throw new IllegalArgumentException("Cannot compare a qualifier version part to a " + o.getClass().descriptorString());
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
}
