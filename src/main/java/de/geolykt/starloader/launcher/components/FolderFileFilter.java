package de.geolykt.starloader.launcher.components;

import java.io.File;

import javax.swing.filechooser.FileFilter;

public class FolderFileFilter extends FileFilter implements java.io.FileFilter {

    // This is a singleton as the class in state independent and there is little reason to create multiple instances of it
    public static final FolderFileFilter INSTANCE = new FolderFileFilter();

    private FolderFileFilter() {
        // Reduce visibility as it is a singleton and should only be instantiated by this class
    }

    @Override
    public boolean accept(File pathname) {
        return pathname.isDirectory();
    }

    @Override
    public String getDescription() {
        return "Folders";
    }
}
