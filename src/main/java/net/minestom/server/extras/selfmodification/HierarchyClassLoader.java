package net.minestom.server.extras.selfmodification;

import java.io.InputStream;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.LinkedList;
import java.util.List;

import org.jetbrains.annotations.NotNull;

/**
 * Classloader part of a hierarchy of classloader.
 */
public abstract class HierarchyClassLoader extends URLClassLoader {
    protected final List<MinestomExtensionClassLoader> children = new LinkedList<>();

    public HierarchyClassLoader(String name, URL[] urls, ClassLoader parent) {
        super(name, urls, parent);
    }

    public void addChild(@NotNull MinestomExtensionClassLoader loader) {
        children.add(loader);
    }

    public InputStream getResourceAsStreamWithChildren(String name) {
        InputStream in = getResourceAsStream(name);
        if (in != null) {
            return in;
        }

        for (MinestomExtensionClassLoader child : children) {
            InputStream childInput = child.getResourceAsStreamWithChildren(name);
            if (childInput != null) {
                return childInput;
            }
        }
        return null;
    }

    public void removeChildInHierarchy(MinestomExtensionClassLoader child) {
        children.remove(child);
        children.forEach(c -> c.removeChildInHierarchy(child));
    }

    @Override
    public String findLibrary(String libname) {
        // We are increasing the visibility of this method knowingly to suppress an issue with LWJGL running code it shouldn't run.
        return super.findLibrary(libname);
    }
}
