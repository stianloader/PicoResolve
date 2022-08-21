package de.geolykt.mavenresolver.misc;

import java.util.Iterator;

import org.dom4j.Element;

public class ChildElementIterable implements Iterable<Element> {

    private final Element parent;

    public ChildElementIterable(Element parent) {
        this.parent = parent;
    }

    @Override
    public Iterator<Element> iterator() {
        return parent.elementIterator();
    }
}
