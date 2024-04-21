package org.stianloader.picoresolve.version;

interface MavenVersionPart extends Comparable<MavenVersionPart> {

    int getPrefixCodepoint();

    String stringifyContent();
}
