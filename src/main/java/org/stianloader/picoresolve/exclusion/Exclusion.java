package org.stianloader.picoresolve.exclusion;

import java.util.Objects;

public class Exclusion implements Excluder {
    private final String group;
    private final String artifact;

    public Exclusion(String group, String artifact) {
        this.group = group;
        this.artifact = artifact;
    }

    @Override
    public boolean isExcluding(String group, String artifact) {
        // TODO are wildcards also valid for partial matches? (Whatever that means)
        if (!this.group.equals("*") && !this.group.equals(group)) {
            return false;
        }
        if (!this.artifact.equals("*") && !this.artifact.equals(artifact)) {
            return false;
        }
        return true;
    }

    @Override
    public int hashCode() {
        return Objects.hash(this.group, this.artifact);
    }

    @Override
    public boolean equals(Object obj) {
        if (obj instanceof Exclusion) {
            return ((Exclusion) obj).artifact.equals(this.artifact) && ((Exclusion) obj).group.equals(this.group);
        }

        return false;
    }
}
