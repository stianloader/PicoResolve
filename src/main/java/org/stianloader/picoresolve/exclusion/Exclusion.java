package org.stianloader.picoresolve.exclusion;

public record Exclusion(String group, String artifact) implements Excluder {
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
}
