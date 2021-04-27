package de.geolykt.starloader.mod;

import java.io.File;

/**
 * A prototype of an extension. This class is different to the DiscoveredExtension class
 * as it is only used within the launcher to explain whether a jar should be loaded or not.
 */
public class ExtensionPrototype {
    public final String name;
    public final String version;
    public final File origin;

    public boolean enabled = false;

    public ExtensionPrototype(File file, String name, String version) {
        this.origin = file;
        this.name = name;
        this.version = version;
    }
}
