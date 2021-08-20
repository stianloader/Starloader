package de.geolykt.starloader.transformers;

import org.objectweb.asm.tree.ClassNode;
import org.jetbrains.annotations.NotNull;
import org.objectweb.asm.Type;

/**
 * A bytecode editing API that interfaces with objectweb ASM {@link ClassNode ClassNodes}.
 * This allows for harmonious runtime editing of classes without the use of agents.
 *
 * @since 2.1.0
 */
public abstract class ASMTransformer {

    /**
     * Transforms the provided classnode to the transformer's liking.
     * Must return true if the class node has been modified, should
     * it not returns false, then the transformation MAY be discarded,
     * however it could also not be discarded. It is best to assume that
     * both are equally possible events.
     *
     * @param source The source node
     * @return True if the node has been modified.
     * @since 2.1.0
     */
    public abstract boolean accept(@NotNull ClassNode node);

    /**
     * Checks whether the given class denoted by the internal name would
     * be a valid potential transformation target.
     * If this method returns false, then {@link #accept(ClassNode)} should
     * NOT be called, however if it returns true, then it may get called.
     * {@link #accept(ClassNode)} will not always return true when this method does,
     * however that method should never return true when this method returns false.
     *
     * @param internalName The internal name of the class
     * @return Whether it is a potential target
     * @see Type#getInternalName()
     * @since 2.1.0
     */
    public abstract boolean isValidTraget(@NotNull String internalName);

    /**
     * Called everytime {@link #accept(ClassNode)} is called and returns true and is used
     * to verify whether this transformer is still needed. Should this method returns false
     * then the transformer is removed from the classloader's transformer pool.
     *
     * @return Whether this transformer is still needed
     * @since 2.1.0
     */
    public boolean isValid() {
        return true;
    }
}
