package de.geolykt.starloader.mod;

import java.net.URL;
import java.util.List;

/**
 * A prototype of an extension. This class is different to the DiscoveredExtension class
 * as it is only used within the launcher to explain whether a jar should be loaded or not.
 */
public class ExtensionPrototype {
    public final List<URL> originURLs;
    public boolean enabled;

    public ExtensionPrototype(List<URL> originURLs) {
        this(originURLs, false);
    }

    public ExtensionPrototype(List<URL> originURLs, boolean enabled) {
        this.originURLs = originURLs;
        this.enabled = enabled;
    }

    @Override
    public String toString() {
        return "ExtensionPrototype[URLs=" + this.originURLs + "]";
    }
}
