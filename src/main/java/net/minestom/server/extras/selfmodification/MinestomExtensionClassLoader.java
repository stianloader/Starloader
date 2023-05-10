package net.minestom.server.extras.selfmodification;

import java.io.InputStream;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.CodeSigner;
import java.security.CodeSource;

import org.jetbrains.annotations.ApiStatus.ScheduledForRemoval;

import de.geolykt.starloader.util.JavaInterop;

public class MinestomExtensionClassLoader extends HierarchyClassLoader {

    /**
     * Root ClassLoader, everything goes through it before any attempt at loading is done inside this classloader.
     */
    private final MinestomRootClassLoader root;
    private static final boolean DEBUG = Boolean.getBoolean("classloader.debug");
    private static final boolean DUMP = DEBUG || Boolean.getBoolean("classloader.dump");

    public MinestomExtensionClassLoader(String name, URL[] urls, MinestomRootClassLoader root) {
        super(name, urls, root);
        this.root = root;
    }

    @Override
    public Class<?> loadClass(String name) throws ClassNotFoundException {
        return this.loadClass(name, false);
    }

    @Override
    protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
        try {
            return this.loadClassAsChild(name, resolve);
        } catch (ClassNotFoundException cnfe) {
            try {
                return root.loadClass(name, resolve);
            } catch (ClassNotFoundException e) {
                e.addSuppressed(cnfe);
                throw e;
            }
        }
    }

    /**
     * Assumes the name is not null, nor it does represent a protected class.
     *
     * @param name
     * @return The loaded class
     * @throws ClassNotFoundException if the class is not found inside this classloader
     */
    public Class<?> loadClassAsChild(String name, boolean resolve) throws ClassNotFoundException {
        Class<?> loadedClass = findLoadedClass(name);
        if (loadedClass != null) {
            return loadedClass;
        }

        try {
            // not in children, attempt load in this classloader
            String path = name.replace(".", "/") + ".class";
            URL url = this.findResource(path);
            if (url == null) {
                throw new ClassNotFoundException("Could not find class " + name);
            }
            try (InputStream in = url.openStream()) {
                if (in == null) {
                    throw new AssertionError();
                }
                byte[] bytes = JavaInterop.readAllBytes(in);
                bytes = root.transformBytes(bytes, name);
                if (DUMP) {
                    Path parent = Paths.get("classes", path).getParent();
                    if (parent != null) {
                        Files.createDirectories(parent);
                    }
                    Files.write(Paths.get("classes", path), bytes);
                }
                String urlPath = url.getPath();
                int seperatorIndex = urlPath.lastIndexOf('!');
                if (seperatorIndex != -1) {
                    url = new URL(urlPath.substring(0, seperatorIndex));
                }
                Class<?> clazz = defineClass(name, bytes, 0, bytes.length, new CodeSource(url, (CodeSigner[]) null));
                if (resolve) {
                    resolveClass(clazz);
                }
                return clazz;
            } catch (Throwable e) {
                throw new ClassNotFoundException("Could not load class " + name, e);
            }
        } catch (ClassNotFoundException e) {
            for (MinestomExtensionClassLoader child : children) {
                try {
                    Class<?> loaded = child.loadClassAsChild(name, resolve);
                    return loaded;
                } catch (ClassNotFoundException e1) {
                    // move on to next child
                    e.addSuppressed(e1);
                }
            }
            throw e;
        }
    }

    @Override
    @Deprecated
    @ScheduledForRemoval
    protected void finalize() throws Throwable {
        super.finalize();
        System.err.println("Class loader " + getName() + " finalized.");
    }

    static {
        ClassLoader.registerAsParallelCapable();
    }
}
