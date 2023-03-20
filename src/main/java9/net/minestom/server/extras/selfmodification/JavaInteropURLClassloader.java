package net.minestom.server.extras.selfmodification;

import java.net.URL;
import java.net.URLClassLoader;

class JavaInteropURLClassloader extends URLClassLoader {

    public JavaInteropURLClassloader(String name, URL[] urls, ClassLoader parent) {
        super(name, urls, parent);
    }

    static {
        ClassLoader.registerAsParallelCapable();
    }
}
