package de.geolykt.mavenresolver.version;

interface MavenVersionPart extends Comparable<MavenVersionPart> {

    int getPrefixCodepoint();

    String stringifyContent();
}
