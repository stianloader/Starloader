package de.geolykt.starloader.launcher;

import java.net.URI;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.objectweb.asm.tree.ClassNode;
import org.slf4j.LoggerFactory;
import org.stianloader.micromixin.transform.api.MixinTransformer;
import org.stianloader.sll.transform.CodeTransformer;

import net.minestom.server.extras.selfmodification.HierarchyClassLoader;
import net.minestom.server.extras.selfmodification.MinestomRootClassLoader;

import de.geolykt.starloader.transformers.ASMTransformer;

public final class ASMMixinTransformer extends ASMTransformer implements CodeTransformer {
    // TODO: Refuse classloading for any mixin classes

    public final MixinTransformer<HierarchyClassLoader> transformer;

    public ASMMixinTransformer(MixinTransformer<HierarchyClassLoader> transformer) {
        this.transformer = transformer;
    }

    @Override
    public boolean accept(@NotNull ClassNode source) {
        if (MinestomRootClassLoader.getInstance().isThreadLoggingClassloadingFailures()) {
            try {
                throw new RuntimeException("Stacktrace");
            } catch (RuntimeException e) {
                LoggerFactory.getLogger(ASMMixinTransformer.class).warn("ASMMixinTransformer implements CodeTransformer, meaning that CodeTransformer#transformClass should be called instead of ASMTransformer#accept. Please report this issue to the caller. Note: This is not a fatal issue, but should be handled in due time.", e);
            }
        }

        return this.transformClass(source, null);
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

    @Override
    public boolean transformClass(@NotNull ClassNode node, @Nullable URI codeSourceURI) {
        this.transformer.transform(node, codeSourceURI);
        return true;
    }
}
