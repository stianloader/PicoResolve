package org.stianloader.picoresolve.internal;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Objects;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

public class XMLUtil {

    public static class ChildElementIterable implements Iterable<@NotNull Element> {

        @NotNull
        private final Element parent;

        public ChildElementIterable(@NotNull Element parent) {
            this.parent = Objects.requireNonNull(parent, "parent may not be null");
        }

        @Override
        public Iterator<@NotNull Element> iterator() {
            return new ElementNodeListIterator(this.parent.getChildNodes());
        }
    }

    public static class ElementNodeListIterator implements Iterator<@NotNull Element> {
        private int i = 0;
        private final NodeList nodeList;

        public ElementNodeListIterator(NodeList list) {
            this.nodeList = list;
            this.hasNext();
        }

        @Override
        public boolean hasNext() {
            if (this.i >= this.nodeList.getLength()) {
                return false;
            }
            if (this.nodeList.item(this.i) instanceof Element) {
                return true;
            }
            this.i++;
            return this.hasNext();
        }

        @SuppressWarnings("null")
        @Override
        @NotNull
        public Element next() {
            if (!this.hasNext()) {
                throw new NoSuchElementException("Iterator exausted: i = " + this.i + ", len = " + this.nodeList.getLength());
            }
            return (Element) this.nodeList.item(this.i++);
        }
    }

    public static class NodeListIterator implements Iterator<@NotNull Node> {
        private int i = 0;
        private final NodeList nodeList;

        public NodeListIterator(NodeList list) {
            this.nodeList = list;
        }

        @Override
        public boolean hasNext() {
            return this.i <= this.nodeList.getLength();
        }

        @SuppressWarnings("null")
        @Override
        @NotNull
        public Node next() {
            return this.nodeList.item(this.i++);
        }
    }

    @Nullable
    public static String elementText(@NotNull Element node, @NotNull String name) {
        Element e = XMLUtil.optElement(node, name);
        if (e == null) {
            return null;
        }
        return e.getTextContent();
    }

    @NotNull
    public static List<@NotNull Element> getChildElements(@NotNull Element parent) {
        List<@NotNull Element> collected = new ArrayList<>();
        for (Element child : new ChildElementIterable(parent)) {
            collected.add(child);
        }
        return collected;
    }

    @Nullable
    public static Element optElement(@NotNull Element parentNode, @NotNull String name) {
        Iterator<@NotNull Element> it = new ElementNodeListIterator(parentNode.getElementsByTagName(name));

        while (it.hasNext()) {
            Element e = it.next();
            // getElementsByTagName is not a shallow operation. That is not requested - so we shall filter out everything that doesn't match
            if (e.getParentNode() != parentNode) {
                continue;
            }
            return e;
        }

        return null;
    }

    @NotNull
    public static Element reqElement(@NotNull Element parentNode, @NotNull String name) {
        Element e = XMLUtil.optElement(parentNode, name);
        if (e == null) {
            throw new NoSuchElementException("No element tagged " + name + " for node " + parentNode.getBaseURI());
        }
        return e;
    }
}
