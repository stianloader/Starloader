package net.minestom.server.extras.selfmodification;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.LinkedList;
import java.util.List;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.LoggerFactory;

/**
 * Classloader part of a hierarchy of classloader.
 */
public abstract class HierarchyClassLoader extends JavaInteropURLClassloader {
    static {
        ClassLoader.registerAsParallelCapable();
    }

    protected final List<MinestomExtensionClassLoader> children = new LinkedList<>();
    protected final List<HierarchyClassLoader> parents = new LinkedList<>();

    public HierarchyClassLoader(String name, URL[] urls, ClassLoader parent) {
        super(name, urls, parent);
    }

    public void addChild(@NotNull MinestomExtensionClassLoader loader) {
        synchronized (HierarchyClassLoader.class) {
            this.children.add(loader);
            loader.parents.add(this);
        }
    }

    @Override
    public String findLibrary(String libname) {
        // We are increasing the visibility of this method knowingly to suppress an issue with LWJGL running code it shouldn't run.
        return super.findLibrary(libname);
    }

    /**
     * Find a resource under the given name within this classloader, or if that
     * fails, in any of it's children. If the resource is found, the resource is
     * opened as an {@link InputStream}, otherwise {@code null} is returned.
     *
     * <p>This method distinguishes itself from {@link #getResourceAsStream(String)},
     * which will first query the parent classloader and only then this classloader.
     * Further, {@link #getResourceAsStream(String)} does not query children classloaders.
     *
     * <p>When failing to open a resource using {@link URL#openStream()}, the resource
     * is deemed to no exist in this classloader and will search in children classloaders.
     *
     * <p>The {@link InputStream} returned by this method must be closed manually, it is
     * unspecified whether the returned object will be closed whenever this classloader
     * is closed. A warning will be logged in that circumstance.
     *
     * @param name The pathname of the resource. See {@link #findResource(String)}.
     * @return The {@link InputStream} of the corresponding found resource, or null if such a resource does not exist.
     * @implNote Search occurs depth-first, which means that registration order can matter quite a lot.
     * @since 0.0.1
     */
    @Nullable
    public InputStream getResourceAsStreamWithChildren(@NotNull String name) {
        URL url = this.findResource(name);
        if (url != null) {
            try {
                return url.openStream();
            } catch (IOException e) {
                LoggerFactory.getLogger(HierarchyClassLoader.class).warn("Unable to open URL '{}' (from pathname '{}') from classloader '{}'!", url, name, this.getName(), e);
            }
        }

        for (MinestomExtensionClassLoader child : this.children) {
            InputStream childInput = child.getResourceAsStreamWithChildren(name);
            if (childInput != null) {
                return childInput;
            }
        }
        return null;
    }

    public void removeChildInHierarchy(MinestomExtensionClassLoader child) {
        synchronized (HierarchyClassLoader.class) {
            this.children.remove(child);
            this.children.forEach(c -> c.removeChildInHierarchy(child));
        }
    }
}
