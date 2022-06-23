package de.geolykt.starloader.launcher;

import java.io.IOException;
import java.io.InputStream;

public class CompletionAnnounceInputStream extends InputStream {

    final long size;
    long tillNextPercent;
    int percent;
    final InputStream in;

    public CompletionAnnounceInputStream(InputStream in, long size) {
        this.in = in;
        this.size = size;
    }

    @Override
    public int read() throws IOException {
        int x = in.read();
        if (x > 0) {
            if (size != -1 && --tillNextPercent == 0) {
                percent++;
                tillNextPercent = size / 100;
                System.out.println(percent + "% done");
            }
        }
        return x;
    }

    @Override
    public int read(byte[] b, int off, int len) throws IOException {
        int bytes = in.read(b, off, len);

        if (size != -1 && (tillNextPercent -= bytes) <= 0) {
            percent++;
            System.out.println(percent + "% done");
            tillNextPercent += size / 100;
        }
        return bytes;
    }
}
