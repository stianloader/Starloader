import org.spongepowered.asm.service.IGlobalPropertyService;
import org.spongepowered.asm.service.IMixinService;
import org.spongepowered.asm.service.IMixinServiceBootstrap;

import net.minestom.server.extras.selfmodification.mixins.GlobalPropertyServiceMinestom;

import de.geolykt.starloader.launcher.service.SLMixinBootstrap;
import de.geolykt.starloader.launcher.service.SLMixinService;

open module de.geolykt.starloader.launcher {
    requires transitive org.objectweb.asm.commons;
    requires transitive org.objectweb.asm.util;
    requires transitive org.objectweb.asm;
    requires transitive org.spongepowered.mixin;
    requires transitive com.google.gson;
    requires transitive org.jetbrains.annotations;
    requires transitive org.slf4j;

    // Stuff we use but don't expose to everyone
    requires org.json;
    requires java.base;
    requires ch.qos.logback.classic;
    requires ch.qos.logback.core;

    exports de.geolykt.starloader.mod;
    exports de.geolykt.starloader.transformers;

    exports net.minestom.server.extras.selfmodification;
    exports net.minestom.server.extras.selfmodification.mixins;

    // Services
    provides IGlobalPropertyService with GlobalPropertyServiceMinestom;
    provides IMixinService with SLMixinService;
    provides IMixinServiceBootstrap with SLMixinBootstrap;

    exports de.geolykt.starloader.launcher.service to org.spongepowered.mixin;
}
