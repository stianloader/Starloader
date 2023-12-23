package de.geolykt.starloader.transformers;

import java.net.URL;

import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class RawClassData {

    private final byte @NotNull[] bytes;

    @Nullable
    private final URL source;

    public RawClassData(@Nullable URL source, byte @NotNull[] bytes) {
        this.source = source;
        this.bytes = bytes;
    }

    @Contract(pure = true)
    public byte @NotNull[] getBytes() {
        return this.bytes;
    }

    @Nullable
    @Contract(pure = true)
    public URL getSource() {
        return this.source;
    }
}
