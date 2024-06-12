module org.stianloader.picoresolve {
    requires transitive java.xml;
    requires transitive static org.jetbrains.annotations;
    requires static org.slf4j;

    exports org.stianloader.picoresolve;
    exports org.stianloader.picoresolve.exclusion;
    exports org.stianloader.picoresolve.logging;
    exports org.stianloader.picoresolve.repo;
    exports org.stianloader.picoresolve.version;
}
