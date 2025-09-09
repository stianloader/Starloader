open module de.geolykt.starloader.launcher {
    requires transitive java.base;

    requires transitive org.objectweb.asm.commons;
    requires transitive org.objectweb.asm.util;
    requires transitive org.objectweb.asm;
    requires transitive org.jetbrains.annotations;
    requires transitive org.slf4j;

    // Stuff we use but don't expose to everyone
    requires ch.qos.logback.classic;
    requires ch.qos.logback.core;
    requires de.geolykt.starloader.ras;
    requires org.json;
    requires org.stianloader.micromixin.transform;
    requires org.stianloader.micromixin.annotations;
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
