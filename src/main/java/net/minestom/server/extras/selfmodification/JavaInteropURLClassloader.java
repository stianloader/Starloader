package net.minestom.server.extras.selfmodification;

import java.net.URL;
import java.net.URLClassLoader;

class JavaInteropURLClassloader extends URLClassLoader {

    private final String name;

    public JavaInteropURLClassloader(String name, URL[] urls, ClassLoader parent) {
        super(urls, parent);
        this.name = name;
    }

    public String getName() {
        return this.name;
    }

    static {
        ClassLoader.registerAsParallelCapable();
    }
}
