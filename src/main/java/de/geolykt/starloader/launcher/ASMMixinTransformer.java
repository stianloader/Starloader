package de.geolykt.starloader.launcher;

import org.jetbrains.annotations.NotNull;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.MixinEnvironment;
import org.spongepowered.asm.mixin.transformer.IMixinTransformer;
import org.spongepowered.asm.mixin.transformer.IMixinTransformerFactory;

import de.geolykt.starloader.launcher.service.SLMixinService;
import de.geolykt.starloader.transformers.ASMTransformer;

public final class ASMMixinTransformer extends ASMTransformer {

    private final IMixinTransformer transformer;

    public ASMMixinTransformer(SLMixinService service) {
        IMixinTransformerFactory factory = service.getMixinInternal(IMixinTransformerFactory.class);
        if (factory == null) {
            throw new NullPointerException("Unable to create IMixinTransformer instance as it's factory went unregistered.");
        }
        transformer = factory.createTransformer();
    }

    @Override
    public boolean accept(@NotNull ClassNode source) {
        boolean ret = transformer.transformClass(MixinEnvironment.getEnvironment(MixinEnvironment.Phase.DEFAULT), source.name.replace("/", "."), source);
        return ret;
    }

    @Override
    public boolean isValidTarget(@NotNull String internalName) {
        return true; // TODO check whether we can evaluate whether a class is a target of a mixin or not
    }

    @Override
    public boolean isValid() {
        return true;
    }
}
