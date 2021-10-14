package de.geolykt.starloader.launcher.service;

import org.spongepowered.asm.service.IMixinServiceBootstrap;

public class SLMixinBootstrap implements IMixinServiceBootstrap {

    @Override
    public String getName() {
        return "Starloader MixinBootstrap";
    }

    @Override
    public String getServiceClassName() {
        return "de.geolykt.starloader.launcher.service.SLMixinService";
    }

    @Override
    public void bootstrap() {
        // Nothing to do
    }
}
