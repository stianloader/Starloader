module de.geolykt.starloader.launcher {
    requires transitive org.objectweb.asm.commons;
    requires transitive org.objectweb.asm.util;
    requires transitive org.objectweb.asm;
    requires transitive com.google.gson;
    requires transitive org.jetbrains.annotations;
    requires transitive org.slf4j;

    // Stuff we use but don't expose to everyone
    requires org.json;
    requires java.base;
    requires ch.qos.logback.classic;
    requires ch.qos.logback.core;
    requires org.stianloader.micromixin.transform;
    requires org.stianloader.micromixin.runtime;

    exports de.geolykt.starloader.mod;
    exports de.geolykt.starloader.transformers;
    exports net.minestom.server.extras.selfmodification;
}
