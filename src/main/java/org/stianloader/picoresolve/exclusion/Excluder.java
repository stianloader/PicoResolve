package org.stianloader.picoresolve.exclusion;

public interface Excluder {
    boolean isExcluding(String group, String artifact);
}
