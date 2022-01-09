package de.geolykt.starloader.transformers;

import java.net.URL;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class RawClassData {

    private final byte[] bytes;

    @Nullable
    private final URL source;

    public RawClassData(@Nullable URL source, byte[] bytes) {
        this.source = source;
        this.bytes = bytes;
    }

    public byte @NotNull [] getBytes() { // I know, you wonder why this is legal. But eclipse be eclipse and I cannot do it otherwise
        byte[] bytes = this.bytes;
        if (bytes == null) {
            throw new NullPointerException();
        }
        return bytes;
    }

    public URL getSource() {
        return source;
    }
}
