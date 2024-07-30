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

    /**
     * Obtains whether the current thread should be logging classloading-related
     * exceptions for this specific classloader. This may relate to class transformation
     * issues, I/O issues, illegal bytecode among other potential causes. The reason
     * SLL logs these kinds of issues by default is that at times the exceptions that are
     * thrown by the classloader are completely absorbed by the caller. This very easily
     * results in rather hard to trace issues as people will usually not expect a
     * classloading failure as the cause of a bug. This is especially relevant in
     * asynchronous environments where exception swallowing is already very likely in
     * conjunction with faulty ASM Transformers resulting in either a transformation
     * failure or illegal bytecode to be emitted.
     *
     * <p>Regardless of this setting, the classloader will always rethrow the exception
     * (or wrap it appropriately, attaching necessary debug info if applicable).
     * By default this setting is set to <code>true</code> and is on a per-thread basis,
     * that is the value is backed by a {@link ThreadLocal} and will thus have concurrency
     * behaviours that align with those of {@link ThreadLocal}.
     *
     * <p>Reasons why this setting may be disabled is either because failures are expected
     * (as is the case in the micromixin test framework for example) or because the thrown
     * exceptions are already being logged and no duplication should occur.
     * Generally disabling logging of these issues should be temporary in nature only
     * where it acutely applies.
     *
     * @return Whether the current thread should log classloading failures explicitly.
     * @since 4.0.0-a20240730
     */
    @Contract(pure = true)
    @AvailableSince(value = "4.0.0-a20240730")
    boolean isThreadLoggingClassloadingFailures();

    /**
     * Sets whether the current thread should be logging classloading-related
     * exceptions for this specific classloader. This may relate to class transformation
     * issues, I/O issues, illegal bytecode among other potential causes. The reason
     * SLL logs these kinds of issues by default is that at times the exceptions that are
     * thrown by the classloader are completely absorbed by the caller. This very easily
     * results in rather hard to trace issues as people will usually not expect a
     * classloading failure as the cause of a bug. This is especially relevant in
     * asynchronous environments where exception swallowing is already very likely in
     * conjunction with faulty ASM Transformers resulting in either a transformation
     * failure or illegal bytecode to be emitted.
     *
     * <p>Regardless of this setting, the classloader will always rethrow the exception
     * (or wrap it appropriately, attaching necessary debug info if applicable).
     * By default this setting is set to <code>true</code> and is on a per-thread basis,
     * that is the value is backed by a {@link ThreadLocal} and will thus have concurrency
     * behaviours that align with those of {@link ThreadLocal}.
     *
     * <p>Reasons why this setting may be disabled is either because failures are expected
     * (as is the case in the micromixin test framework for example) or because the thrown
     * exceptions are already being logged and no duplication should occur.
     * Generally disabling logging of these issues should be temporary in nature only
     * where it acutely applies.
     *
     * @param logFailures Whether the current thread should log classloading failures explicitly.
     * @return The current {@link TransformableClassloader} instance, for chaining.
     * @since 4.0.0-a20240730
     */
    @Contract(pure = false, value = "_ -> this")
    @AvailableSince(value = "4.0.0-a20240730")
    TransformableClassloader setThreadLoggingClassloadingFailures(boolean logFailures);

    @NotNull
    @Contract(pure = false)
    @CheckReturnValue
    @AvailableSince(value = "4.0.0-a20231223")
    Class<?> transformAndDefineClass(@NotNull String className, @NotNull RawClassData data);
}
