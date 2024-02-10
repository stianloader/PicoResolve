module de.geolykt.picoresolve {
    requires transitive org.dom4j;
    requires transitive java.xml;
    requires static org.jetbrains.annotations;

    exports de.geolykt.picoresolve;
    exports de.geolykt.picoresolve.exclusion;
    exports de.geolykt.picoresolve.repo;
    exports de.geolykt.picoresolve.version;
}
