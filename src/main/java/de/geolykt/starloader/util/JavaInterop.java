package de.geolykt.starloader.util;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import net.minestom.server.extras.selfmodification.HierarchyClassLoader;

public final class JavaInterop {

    @SuppressWarnings("null")
    @NotNull
    public static final String getClassloaderName(ClassLoader loader) {
        if (loader instanceof HierarchyClassLoader) {
            return ((HierarchyClassLoader) loader).getName();
        }
        return loader.toString();
    }

    public static final boolean isJava9() {
        return false;
    }

    public static final byte @NotNull[] readAllBytes(@NotNull InputStream in) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        byte[] buffer = new byte[4096];
        for (int read = in.read(buffer); read != -1; read = in.read(buffer)) {
            baos.write(buffer, 0, read);
        }
        return baos.toByteArray();
    }

    @Nullable
    public static final ClassLoader getPlattformClassloader() {
        return null;
    }
}
