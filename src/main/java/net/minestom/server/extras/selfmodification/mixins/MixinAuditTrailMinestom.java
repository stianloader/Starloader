package net.minestom.server.extras.selfmodification.mixins;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongepowered.asm.service.IMixinAuditTrail;

import de.geolykt.starloader.Starloader;

/**
 * Takes care of logging mixin operations
 */
public class MixinAuditTrailMinestom implements IMixinAuditTrail {

    public final static Logger LOGGER = LoggerFactory.getLogger(Starloader.class);

    @Override
    public void onApply(String className, String mixinName) {
        LOGGER.trace("Applied mixin {} to class {}", mixinName, className);
    }

    @Override
    public void onPostProcess(String className) {
        LOGGER.trace("Post processing {}", className);
    }

    @Override
    public void onGenerate(String className, String generatorName) {
        LOGGER.trace("Generating class {} via generator {}", className, generatorName);
    }
}
