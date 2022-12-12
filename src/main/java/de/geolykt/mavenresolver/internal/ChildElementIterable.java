package de.geolykt.mavenresolver.internal;

import java.util.Iterator;

import org.dom4j.Element;

public class ChildElementIterable implements Iterable<Element> {

    private final Element parent;

    public ChildElementIterable(Element parent) {
        if (parent == null) {
            throw new NullPointerException("The argument \"parent\" is null.");
        }
        this.parent = parent;
    }

    @Override
    public Iterator<Element> iterator() {
        return parent.elementIterator();
    }
}
