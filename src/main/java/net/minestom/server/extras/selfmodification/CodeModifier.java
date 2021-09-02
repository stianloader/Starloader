package net.minestom.server.extras.selfmodification;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.objectweb.asm.tree.ClassNode;

import de.geolykt.starloader.transformers.ASMTransformer;

/**
 * Will be called by {@link MinestomRootClassLoader} to transform classes at load-time
 *
 * @deprecated Replaced with {@link ASMTransformer}.
 */
@Deprecated(forRemoval = true, since = "2.1.0")
public abstract class CodeModifier extends ASMTransformer {
    /**
     * Must return true iif the class node has been modified
     *
     * @param source The source node
     * @return True if the node has been modified.
     */
    public abstract boolean transform(ClassNode source);

    /**
     * Beginning of the class names to transform.
     * 'null' is allowed to transform any class, but not recommended
     *
     * @return The namespace where the modifier is working from
     */
    public abstract @Nullable String getNamespace();

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean accept(@NotNull ClassNode node) {
        return transform(node);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean isValidTraget(@NotNull String internalName) {
        String namespace = getNamespace();
        return namespace == null || internalName.replace('/', '.').startsWith(namespace);
    }
}
