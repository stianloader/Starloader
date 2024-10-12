package org.stianloader.sll.transform;

import java.net.URI;
import java.security.CodeSource;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.objectweb.asm.tree.ClassNode;

import de.geolykt.starloader.transformers.ASMTransformer;

/**
 * An experimental extension to the current ASM {@link ClassNode} transformation API
 * of SLL. That is, this interface is implemented alongside {@link ASMTransformer},
 * which results in {@link CodeTransformer#transformClass(ClassNode, URI)} to be called
 * instead of {@link ASMTransformer#accept(ClassNode)}.
 *
 * <p>As of now, this API is currently mainly intended for internal use within the
 * micromixin variant of SLL and may require more throughout vetting for other usecases,
 * which is why usage of it is not recommended even if it is intended to replace the
 * current bytecode transformation API in SLL which has existed in it's current state since
 * SLL 2.1.0. As such, ABI (Application binary interface) breaks for this interface may
 * occur in future versions even though breaks are atypical for SLL.
 *
 * @since 4.0.0-a20241012
 */
@ApiStatus.Experimental
@ApiStatus.AvailableSince("4.0.0-a20241012")
public interface CodeTransformer {

    /**
     * Optionally transforms a class, returning <code>true</code> if the input
     * <code>node</code> was modified, <code>false</code> otherwise.
     *
     * <p>If the node was transformed while this method returned <code>false</code>,
     * the applied transformations <b>may</b> get discarded, or may also not get discarded.
     * The exact behaviour will likely depend on the behaviour of other classes. Please
     * be aware that debugging such edge case behaviour is extremely difficult as the
     * cause and effects will likely seem to be completely disconnected to the average
     * programmer, and will further not be reproducible in every environment.
     * The return flag mainly exists for performance reasons as converting {@link ClassNode}
     * into <code>byte[]</code> isn't exactly free.
     *
     * <p>The transformation occurs in-situ, meaning that the input {@link ClassNode} is transformed
     * as-is without any cloning. Thus the input node is also the output node in some sense.
     *
     * @param node The input and output {@link ClassNode} to transform.
     * @param codeSourceURI The {@link URI} where this class is located in, following the
     * paradigms of {@link CodeSource#getLocation()}. For classes located inside JARs, the
     * URI of the jar will be used. For classes within directories, the URI of the directory
     * is used (i.e. <code>file://bin/</code> for <code>file://bin/com/example/Main.class</code>).
     * If the location of the class file is unknown or cannot be represented as an {@link URI},
     * <code>null</code> should be used.
     * @return <code>true</code> if the input node was transformed, <code>false</code> otherwise.
     * @since 4.0.0-a20241012
     */
    @ApiStatus.Experimental
    @ApiStatus.AvailableSince("4.0.0-a20241012")
    boolean transformClass(@NotNull ClassNode node, @Nullable URI codeSourceURI);
}
