package de.geolykt.picoresolve.exclusion;

public interface Excluder {
    boolean isExcluding(String group, String artifact);
}
