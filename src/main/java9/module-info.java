module de.geolykt.starloader.launcher {
    requires transitive org.objectweb.asm.commons;
    requires transitive org.objectweb.asm.util;
    requires transitive org.objectweb.asm;
    requires transitive org.jetbrains.annotations;
    requires transitive org.slf4j;
    requires transitive java.base;

    // requires on filename-based module name
    requires static reversible.access.setter;

    // Stuff we use but don't expose to everyone
    requires org.json;
    requires ch.qos.logback.classic;
    requires ch.qos.logback.core;
    requires org.stianloader.micromixin.transform;
    requires org.stianloader.micromixin.runtime;
    requires org.stianloader.picoresolve;

    // Exports
    exports de.geolykt.starloader;
    exports de.geolykt.starloader.launcher;
    exports de.geolykt.starloader.mod;
    exports de.geolykt.starloader.transformers;
    exports org.stianloader.sll.transform;
    exports net.minestom.server.extras.selfmodification;

    exports de.geolykt.starloader.util to ch.qos.logback.core;
}
