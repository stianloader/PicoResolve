package de.geolykt.mavenresolver;

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
            return COMPILE;
        }
        return valueOf(element.getText().toUpperCase(Locale.ROOT));
    }
}
