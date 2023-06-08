package de.geolykt.starloader.util;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;

import org.jetbrains.annotations.NotNull;

import net.minestom.server.extras.selfmodification.HierarchyClassLoader;

public final class JavaInterop {

    @SuppressWarnings("null")
    @NotNull
    public static final String getClassloaderName(ClassLoader loader) {
        return loader.getName();
    }

    public static final boolean isJava9() {
        return true;
    }

    public static final byte[] readAllBytes(@NotNull InputStream in) throws IOException {
        return in.readAllBytes();
    }

    public static final ClassLoader getPlattformClassloader() {
        return ClassLoader.getPlatformClassLoader();
    }
}
