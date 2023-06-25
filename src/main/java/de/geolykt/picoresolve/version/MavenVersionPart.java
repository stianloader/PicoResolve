package de.geolykt.picoresolve.version;

interface MavenVersionPart extends Comparable<MavenVersionPart> {

    int getPrefixCodepoint();

    String stringifyContent();
}
