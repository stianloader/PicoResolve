package de.geolykt.picoresolve;

import java.util.Locale;

import org.dom4j.Element;

public enum Scope {

    COMPILE,
    RUNTIME,
    PROVIDED,
    SYSTEM,
    TEST;

    public static Scope fromElement(Element element) {
        if (element == null) {
            return Scope.COMPILE;
        }
        return Scope.valueOf(element.getText().toUpperCase(Locale.ROOT));
    }

    public static Scope fromString(String str) {
        if (str == null) {
            return Scope.COMPILE;
        }
        return Scope.valueOf(str.toUpperCase(Locale.ROOT));
    }
}
