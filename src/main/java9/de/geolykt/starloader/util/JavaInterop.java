package de.geolykt.starloader.util;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.ApiStatus.AvailableSince;

import net.minestom.server.extras.selfmodification.HierarchyClassLoader;

public final class JavaInterop {

    @SuppressWarnings("null")
    @NotNull
    public static final String getClassloaderName(ClassLoader loader) {
        return loader.getName();
    }

    /**
     * Obtains the platform classloader under Java 9 or above. Under Java 8, {@code null} is returned.
     *
     * <p>Keep in mind that while the platform classloader contains the platform classes, it has also
     * access to modules on the modulepath. This means that when using JPMS/Jigsaw, it may load classes
     * that are part of the 'app' classloader. This behaviour contradicts the behaviour for classes
     * present on the classpath, which cannot be loaded through the platform classloader.
     *
     * <p>This method used to be historically available under the name "getPlattformClassloader".
     * This however was a typo, and was corrected to the same name used by the JDK to reduce confusion.
     * The incorrect method name was used between late March 2023 and early September 2025.
     *
     * @return The platform classloader, if present.
     * @since 4.0.0-a20250906
     */
    @NotNull
    @AvailableSince("4.0.0-a20250906")
    public static final ClassLoader getPlatformClassLoader() {
        return ClassLoader.getPlatformClassLoader();
    }

    public static final boolean isJava9() {
        return true;
    }

    public static final byte[] readAllBytes(@NotNull InputStream in) throws IOException {
        return in.readAllBytes();
    }
}
