package de.geolykt.starloader.util;

import java.io.File;
import java.io.FileFilter;

public class JarFilter implements FileFilter {

    public static final JarFilter INSTANCE = new JarFilter();

    private JarFilter() {}

    @Override
    public boolean accept(File pathname) {
        return pathname.getPath().endsWith(".jar");
    }
}
