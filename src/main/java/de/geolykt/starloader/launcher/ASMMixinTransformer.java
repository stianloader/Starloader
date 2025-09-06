package de.geolykt.starloader.launcher;

import java.net.URI;
import java.util.Objects;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.objectweb.asm.tree.ClassNode;
import org.slf4j.LoggerFactory;
import org.spongepowered.asm.mixin.MixinEnvironment;
import org.spongepowered.asm.mixin.transformer.IMixinTransformer;
import org.spongepowered.asm.mixin.transformer.IMixinTransformerFactory;
import org.stianloader.micromixin.backports.MicromixinBackportsBootstrap;
import org.stianloader.sll.transform.CodeTransformer;

import net.minestom.server.extras.selfmodification.MinestomRootClassLoader;

import de.geolykt.starloader.launcher.service.SLMixinService;
import de.geolykt.starloader.transformers.ASMTransformer;

public final class ASMMixinTransformer extends ASMTransformer implements CodeTransformer {
    // TODO: Refuse classloading for any mixin classes

    @NotNull
    private final IMixinTransformer transformer;

    public ASMMixinTransformer(SLMixinService service) {
        IMixinTransformerFactory factory = service.getMixinInternal(IMixinTransformerFactory.class);
        if (factory == null) {
            throw new NullPointerException("Unable to create IMixinTransformer instance as it's factory went unregistered.");
        }
        this.transformer = Objects.requireNonNull(factory.createTransformer(), "factory may not create a null transformer");
        MicromixinBackportsBootstrap.init(this.transformer);
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
    public boolean isValidTarget(@NotNull String internalName, @Nullable URI codeSourceURI) {
        return true; // TODO check whether we can evaluate whether a class is a target of a mixin or not
    }

    @Override
    public boolean isValidTarget(@NotNull String internalName) {
        if (MinestomRootClassLoader.getInstance().isThreadLoggingClassloadingFailures()) {
            try {
                throw new RuntimeException("Stacktrace");
            } catch (RuntimeException e) {
                LoggerFactory.getLogger(ASMMixinTransformer.class).warn("ASMMixinTransformer implements CodeTransformer, meaning that CodeTransformer#isValidTarget(String, URI) should be called instead of ASMTransformer#isValidTarget(String). Please report this issue to the caller. Note: This is not a fatal issue, but should be handled in due time.", e);
            }
        }

        return this.isValidTarget(internalName, null);
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
        boolean ret = this.transformer.transformClass(MixinEnvironment.getEnvironment(MixinEnvironment.Phase.DEFAULT), node.name.replace("/", "."), node);
        return ret;
    }
}
