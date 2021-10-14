package de.geolykt.starloader.launcher.service;

import java.util.Collection;

import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.launch.platform.IMixinPlatformServiceAgent;
import org.spongepowered.asm.launch.platform.MixinPlatformAgentAbstract;
import org.spongepowered.asm.launch.platform.container.IContainerHandle;
import org.spongepowered.asm.util.Constants;

public class SLPlattformAgent extends MixinPlatformAgentAbstract implements IMixinPlatformServiceAgent {

    @Nullable
    private static SLPlattformAgent instance;

    @Override
    public void init() {
        instance = this;
    }

    @Override
    public String getSideName() {
        return Constants.SIDE_UNKNOWN; // Galimulator doesn't really have sides yet. And god knows when multiplayer will be a thing.
    }

    @Override
    public Collection<IContainerHandle> getMixinContainers() {
        return null; // we don't need to register anything
    }

    @Nullable
    public static SLPlattformAgent getInstance() {
        return instance;
    }
}
