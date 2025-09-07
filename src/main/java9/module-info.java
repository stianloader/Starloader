import org.spongepowered.asm.service.IGlobalPropertyService;
import org.spongepowered.asm.service.IMixinService;
import org.spongepowered.asm.service.IMixinServiceBootstrap;

import net.minestom.server.extras.selfmodification.mixins.GlobalPropertyServiceMinestom;

import de.geolykt.starloader.launcher.service.SLMixinBootstrap;
import de.geolykt.starloader.launcher.service.SLMixinService;

open module de.geolykt.starloader.launcher {
    requires transitive java.base;

    requires transitive org.jetbrains.annotations;
    requires transitive org.objectweb.asm.commons;
    requires transitive org.objectweb.asm.util;
    requires transitive org.objectweb.asm;
    requires transitive org.slf4j;
    requires transitive org.spongepowered.mixin;
    requires transitive org.stianloader.micromixin.backports;

    // requires on filename-based module name
    requires static mixinextras.common;

    // Stuff we use but don't expose to everyone
    requires ch.qos.logback.classic;
    requires ch.qos.logback.core;
    requires de.geolykt.starloader.ras;
    requires org.json;
    requires org.stianloader.picoresolve;

    // Exports
    exports de.geolykt.starloader;
    exports de.geolykt.starloader.launcher;
    exports de.geolykt.starloader.mod;
    exports de.geolykt.starloader.transformers;
    exports org.stianloader.sll.transform;
    exports net.minestom.server.extras.selfmodification;

    // Services
    provides IGlobalPropertyService with GlobalPropertyServiceMinestom;
    provides IMixinService with SLMixinService;
    provides IMixinServiceBootstrap with SLMixinBootstrap;

    exports de.geolykt.starloader.launcher.service to org.spongepowered.mixin;
    exports de.geolykt.starloader.util to ch.qos.logback.core;
    exports net.minestom.server.extras.selfmodification.mixins to org.spongepowered.mixin;
}
