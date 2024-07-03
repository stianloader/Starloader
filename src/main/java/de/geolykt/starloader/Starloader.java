package de.geolykt.starloader;

import java.io.File;
import java.lang.reflect.Field;
import java.nio.file.Path;
import java.util.List;

import org.jetbrains.annotations.ApiStatus.Internal;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.minestom.server.extras.selfmodification.MinestomRootClassLoader;

import de.geolykt.starloader.mod.DirectoryExtensionPrototypeList;
import de.geolykt.starloader.mod.Extension;
import de.geolykt.starloader.mod.ExtensionManager;
import de.geolykt.starloader.mod.ExtensionPrototype;
import de.geolykt.starloader.util.JavaInterop;

public final class Starloader {

    private static final Logger LOGGER = LoggerFactory.getLogger(Starloader.class);

    private static Starloader instance;

    private final ExtensionManager extensions;
    @NotNull
    private final List<@NotNull ? extends ExtensionPrototype> extensionSource;
    @NotNull
    private final Path modDirectory;

    private Starloader(@NotNull DirectoryExtensionPrototypeList modSource) {
        this.extensionSource = modSource;
        this.modDirectory = modSource.getFolder().toPath();
        this.extensions = new ExtensionManager(this.modDirectory.resolve(".picoresolve-cache"));
    }

    private Starloader(@NotNull List<@NotNull ? extends ExtensionPrototype> extensionSource, ExtensionManager extensions, @NotNull Path modDir) {
        this.extensionSource = extensionSource;
        this.extensions = extensions;
        this.modDirectory = modDir;
    }

    private void start() {
        this.extensions.loadExtensions(this.extensionSource);
        Starloader.LOGGER.info("From {} prototypes, {} extensions were loaded.",  this.extensionSource.size(),  this.extensions.getExtensions().size());
        long start = System.currentTimeMillis();
        Starloader.LOGGER.info("Initializing extension: preinit");
        this.extensions.getExtensions().forEach(Extension::preInitialize);
        Starloader.LOGGER.info("Initializing extension: init");
        this.extensions.getExtensions().forEach(extension -> {
            extension.initialize();
            Starloader.LOGGER.info("Initialized extension {}.", extension.getDescription().getName());
        });
        Starloader.LOGGER.info("Initializing extension: postinit");
        this.extensions.getExtensions().forEach(Extension::postInitialize);
        Starloader.LOGGER.info("All Extensions initialized within {}ms", (System.currentTimeMillis() - start));
        // FIXME don't use shutdown hooks and/or have them deadlock-proof.
        Runtime.getRuntime().addShutdownHook(new Thread(this.extensions::shutdown, "ExtensionsShutdownThread"));
    }

    @Internal
    public static void start(@NotNull List<@NotNull ExtensionPrototype> extensionSource, @NotNull Path modDir) {
        if (Starloader.instance != null) {
            throw new IllegalStateException("Starloader initialized twice!");
        }
        Starloader.LOGGER.info("Java version: {}. JavaInterop J9: {}", System.getProperty("java.version"), JavaInterop.isJava9());
        Starloader.instance = new Starloader(extensionSource, new ExtensionManager(modDir.resolve(".picoresolve-cache")), modDir);
        Starloader.instance.start();
    }

    @Internal
    public static void start(@NotNull DirectoryExtensionPrototypeList modSource) {
        if (Starloader.instance != null) {
            throw new IllegalStateException("Starloader initialized twice!");
        }
        Starloader.LOGGER.info("Java version: {}. JavaInterop J9: {}", System.getProperty("java.version"), JavaInterop.isJava9());
        Starloader.instance = new Starloader(modSource);
        Starloader.instance.start();
    }

    public static ExtensionManager getExtensionManager() {
        return Starloader.instance.extensions;
    }

    @NotNull
    @Deprecated
    public static File getExtensionDir() {
        return Starloader.instance.getModDirectory().toFile();
    }

    @NotNull
    public Path getModDirectory() {
        return this.modDirectory;
    }

    public static Starloader getInstance() {
        return Starloader.instance;
    }

    static {
        // Usually the Starloader class should be loaded by the root classloader, but in certain
        // cases this does not happen. To fix this, this class delegates all but the #start methods
        // to the actual instance loaded by the root classloader
        label001:
        if (Starloader.class.getClassLoader().getClass() != MinestomRootClassLoader.class) {
            MinestomRootClassLoader rootCl = MinestomRootClassLoader.getInstance();
            try {
                Class<?> slClass = Class.forName("de.geolykt.starloader.Starloader", false, rootCl);
                if (slClass == Starloader.class) {
                    break label001; // Everything is happening as intended. The root classloader is likely delegating it's calls to some other classloader as the other classloader has the class loaded.
                }
                Field instanceField = slClass.getDeclaredField("instance");
                Field srcField = slClass.getDeclaredField("extensionSource");
                Field extField = slClass.getDeclaredField("extensions");
                Field modDirField = slClass.getDeclaredField("modDirectory");
                srcField.setAccessible(true);
                extField.setAccessible(true);
                modDirField.setAccessible(true);
                instanceField.setAccessible(true);
                Object instance = instanceField.get(null);
                if (instance == null) {
                    throw new IllegalStateException("Unable to find instance of actual the Starloader class (Did it start yet?);"
                            + " This Class instance was loaded by " + Starloader.class.getClassLoader() + ", where as it should've been " + slClass.getClassLoader());
                }
                @SuppressWarnings({ "unchecked", "null" })
                List<@NotNull ExtensionPrototype> extensionSource = (List<ExtensionPrototype>) srcField.get(instance);
                ExtensionManager extensions = (ExtensionManager) extField.get(instance);
                Path modDir = (Path) modDirField.get(instance);
                assert extensionSource != null && modDir != null;
                Starloader.instance = new Starloader(extensionSource, extensions, modDir);
            } catch (Exception e) {
                throw new IllegalStateException("This class should be loaded by the root classloader!", e);
            }
        }
    }
}
