package de.geolykt.mavenresolver.version;

public sealed interface MavenVersionPart extends Comparable<MavenVersionPart> permits NumericVersionPart, PrereleaseVersionPart, QualifierVersionPart {

    int getPrefixCodepoint();

    String stringifyContent();
}
