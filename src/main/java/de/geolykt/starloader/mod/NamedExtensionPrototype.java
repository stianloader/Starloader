package de.geolykt.starloader.mod;

import java.net.URL;
import java.util.List;

public class NamedExtensionPrototype extends ExtensionPrototype {
    public final String name;
    public final String version;

    public NamedExtensionPrototype(List<URL> originURLs, String name, String version) {
        super(originURLs, true, null);
        this.name = name;
        this.version = version;
    }

    @Override
    public String toString() {
        return "NamedExtensionPrototype[URLs=" + this.originURLs + ", name=" + this.name + ", version=" + this.version + "]";
    }
}
