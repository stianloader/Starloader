package de.geolykt.starloader.transformers;

import org.jetbrains.annotations.NotNull;
import org.objectweb.asm.tree.ClassNode;
import org.slf4j.LoggerFactory;

import de.geolykt.starloader.ras.ReversibleAccessSetterContext;
import de.geolykt.starloader.ras.ReversibleAccessSetterContext.RASTransformFailure;
import de.geolykt.starloader.ras.ReversibleAccessSetterContext.RASTransformScope;

/**
 * An {@link ASMTransformer} that implements the transformation of class files via reversible access setters (RAS).
 *
 * @since 4.0.0
 */
public class ReversibleAccessSetterTransformer extends ASMTransformer {

    @NotNull
    private final ReversibleAccessSetterContext mainContext = new ReversibleAccessSetterContext(RASTransformScope.RUNTIME, false);
    @NotNull
    private final ReversibleAccessSetterContext reverseContext = new ReversibleAccessSetterContext(RASTransformScope.BUILDTIME, true);

    @Override
    public boolean accept(@NotNull ClassNode node) {
        try {
            this.reverseContext.accept(node);
            this.mainContext.accept(node);
        } catch (RASTransformFailure failure) {
            LoggerFactory.getLogger(getClass()).error("Unable to transform class {}", node.name, failure);
        }
        return true;
    }

    @NotNull
    public ReversibleAccessSetterContext getMainContext() {
        return this.mainContext;
    }

    @Override
    public int getPriority() {
        return -10_010;
    }

    @NotNull
    public ReversibleAccessSetterContext getReverseContext() {
        return this.reverseContext;
    }

    @Override
    public boolean isValidTarget(@NotNull String internalName) {
        return this.mainContext.isTarget(internalName) || this.reverseContext.isTarget(internalName);
    }
}
