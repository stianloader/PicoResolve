package de.geolykt.picoresolve;

import java.util.Locale;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.w3c.dom.Element;

public enum Scope {

    COMPILE,
    RUNTIME,
    PROVIDED,
    SYSTEM,
    TEST;

    @NotNull
    public static Scope fromElement(@Nullable Element element) {
        if (element == null) {
            return Scope.COMPILE;
        }
        return Scope.valueOf(element.getTextContent().toUpperCase(Locale.ROOT));
    }

    @NotNull
    public static Scope fromString(@Nullable String str) {
        if (str == null) {
            return Scope.COMPILE;
        }
        return Scope.valueOf(str.toUpperCase(Locale.ROOT));
    }
}
