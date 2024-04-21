package org.stianloader.picoresolve;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

final record VersionlessDependency(@NotNull String group, @NotNull String artifact, @Nullable String classifier, @Nullable String type) {

    @NotNull
    String getType(@NotNull String defaultValue) {
        String t = this.type;
        if (t == null) {
            return defaultValue;
        }
        return t;
    }
}
