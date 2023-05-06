package de.geolykt.starloader.mod;

import java.net.URL;
import java.util.List;

public class NamedExtensionPrototype extends ExtensionPrototype {
    public final String name;
    public final String version;

    public NamedExtensionPrototype(List<URL> originURLs, String name, String version) {
        super(originURLs);
        this.name = name;
        this.version = version;
    }
}
