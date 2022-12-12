module de.geolykt.resolver {

    requires transitive dom4j;
    requires java.xml;

    exports de.geolykt.mavenresolver;
    exports de.geolykt.mavenresolver.version;
    exports de.geolykt.mavenresolver.repo;
}
