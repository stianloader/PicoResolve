package org.stianloader.picoresolve.version;

import org.stianloader.picoresolve.internal.ConfusedResolverException;

final class NumericVersionPart implements MavenVersionPart {

    private int prefixCodepoint;
    private int value;

    public NumericVersionPart(int prefixCodepoint, int value) {
        this.prefixCodepoint = prefixCodepoint;
        this.value = value;
    }

    @Override
    public int compareTo(MavenVersionPart o) {
        if (o instanceof NumericVersionPart other) {
            if (other.prefixCodepoint != this.prefixCodepoint) {
                if (this.prefixCodepoint == '.' && other.prefixCodepoint == '-') {
                    // '.' is more than '-' for numbers
                    return 1;
                } else if (this.prefixCodepoint == '-' && other.prefixCodepoint == '.') {
                    // '-' is less than '.' for numbers
                    return -1;
                } else {
                    throw new ConfusedResolverException("Prefix codepoint confusion");
                }
            }
            return Integer.compareUnsigned(value, other.value);
        } else if (o instanceof QualifierVersionPart || o instanceof PrereleaseVersionPart) {
            // Numeric version parts are always "more" than qualifier version parts
            return 1;
        } else {
            throw new IllegalArgumentException("Cannot compare a numeric version part to a " + o.getClass().descriptorString());
        }
    }

    @Override
    public int getPrefixCodepoint() {
        return prefixCodepoint;
    }

    @Override
    public String stringifyContent() {
        return Integer.toString(value);
    }

    @Override
    public String toString() {
        return Character.toString(getPrefixCodepoint()) + stringifyContent();
    }
}
