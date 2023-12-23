package de.geolykt.starloader.transformers;

import java.util.Collection;

import org.jetbrains.annotations.ApiStatus.AvailableSince;
import org.jetbrains.annotations.CheckReturnValue;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Unmodifiable;

/**
 * Interface that should be applied on implementations of {@link ClassLoader} to mark that they can be
 * transformed via {@link ASMTransformer ASM Transformers}.
 *
 * <p>This interface exists for abstraction reasons, as SLL's classloader classes will not exist in the
 * minestom package forever - that is, the package in which they exist will be renamed with SLL 5.
 *
 * <p>When implementing your own classloader, you are encouraged to implement this interface for compatibility
 * reasons.
 *
 * @since 4.0.0
 */
@AvailableSince(value = "4.0.0-a20231223")
public interface TransformableClassloader {

    @Contract(pure = false, mutates = "this")
    @AvailableSince(value = "4.0.0-a20231223")
    void addASMTransformer(@NotNull ASMTransformer transformer);

    @NotNull
    @Unmodifiable
    @Contract(pure = true, value = "-> new")
    @AvailableSince(value = "4.0.0-a20231223")
    Collection<@NotNull ASMTransformer> getASMTransformers();

    @NotNull
    @Contract(pure = false)
    @CheckReturnValue
    @AvailableSince(value = "4.0.0-a20231223")
    Class<?> transformAndDefineClass(@NotNull String className, @NotNull RawClassData data);
}
