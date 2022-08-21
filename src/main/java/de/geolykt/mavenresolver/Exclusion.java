package de.geolykt.mavenresolver;

public record Exclusion(String group, String artifact) {

    public boolean isExcluding(GAV gav) {
        // TODO are wildcards also valid for partial matches?
        if (!group.equals("*") && !group.equals(gav.group())) {
            return false;
        }
        if (!artifact.equals("*") && !artifact.equals(gav.artifact())) {
            return false;
        }
        return true;
    }
}
