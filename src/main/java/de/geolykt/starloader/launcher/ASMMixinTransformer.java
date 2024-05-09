package de.geolykt.starloader.launcher;

import org.jetbrains.annotations.NotNull;
import org.objectweb.asm.tree.ClassNode;
import org.stianloader.micromixin.transform.api.MixinTransformer;

import net.minestom.server.extras.selfmodification.HierarchyClassLoader;

import de.geolykt.starloader.transformers.ASMTransformer;

public final class ASMMixinTransformer extends ASMTransformer {
    // TODO: Refuse classloading for any mixin classes

    public final MixinTransformer<HierarchyClassLoader> transformer;

    public ASMMixinTransformer(MixinTransformer<HierarchyClassLoader> transformer) {
        this.transformer = transformer;
    }

    @Override
    public boolean accept(@NotNull ClassNode source) {
        this.transformer.transform(source);
        return true;
    }

    @Override
    public boolean isValidTarget(@NotNull String internalName) {
        return this.transformer.isMixinTarget(internalName);
    }

    @Override
    public boolean isValid() {
        return true;
    }

    @Override
    public int getPriority() {
        return -10_000;
    }
}
