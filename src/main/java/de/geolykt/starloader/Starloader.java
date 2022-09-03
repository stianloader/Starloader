package de.geolykt.starloader;

import java.io.File;
import java.lang.reflect.Field;
import java.util.Objects;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.minestom.server.extras.selfmodification.MinestomRootClassLoader;

import de.geolykt.starloader.launcher.LauncherConfiguration;
import de.geolykt.starloader.mod.Extension;
import de.geolykt.starloader.mod.ExtensionManager;

public final class Starloader {

    private static final Logger LOGGER = LoggerFactory.getLogger(Starloader.class);

    private static Starloader instance;

    private final ExtensionManager extensions;
    private final LauncherConfiguration config;

    private Starloader(LauncherConfiguration config) {
        this.config = config;
        this.extensions = new ExtensionManager();
    }

    private Starloader(LauncherConfiguration config, ExtensionManager extensions) {
        this.config = config;
        this.extensions = extensions;
    }

    private void start() {
        extensions.loadExtensions(config.getExtensionList());
        LOGGER.info("From {} prototypes, {} extensions were loaded.", config.getExtensionList().getPrototypes().size(), extensions.getExtensions().size());
        long start = System.currentTimeMillis();
        LOGGER.info("Initializing extension: preinit");
        extensions.getExtensions().forEach(Extension::preInitialize);
        LOGGER.info("Initializing extension: init");
        extensions.getExtensions().forEach(extension -> {
            extension.initialize();
            LOGGER.info("Initialized extension {}.", extension.getDescription().getName());
        });
        LOGGER.info("Initializing extension: postinit");
        extensions.getExtensions().forEach(Extension::postInitialize);
        LOGGER.info("All Extensions initialized within {}ms", (System.currentTimeMillis() - start));
        Runtime.getRuntime().addShutdownHook(new Thread(() -> { // FIXME don't use shutdown hooks and/or have them deadlock-proof.
            extensions.shutdown();
        }, "ExtensionsShutdownThread"));
    }

    public static void start(LauncherConfiguration config) {
        if (instance != null) {
            throw new IllegalStateException("Starloader initialized twice!");
        }
        LOGGER.info("Java version: {}", System.getProperty("java.version"));
        instance = new Starloader(config);
        instance.start();
    }

    public static ExtensionManager getExtensionManager() {
        return instance.extensions;
    }

    public static File getExtensionDir() {
        return instance.config.getExtensionsFolder();
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
                Field cfgField = slClass.getDeclaredField("config");
                Field extField = slClass.getDeclaredField("extensions");
                cfgField.setAccessible(true);
                extField.setAccessible(true);
                instanceField.setAccessible(true);
                Object instance = instanceField.get(null);
                if (instance == null) {
                    throw new IllegalStateException("Unable to find instance of actual the Starloader class (Did it start yet?);"
                            + " This Class instance was loaded by " + Starloader.class.getClassLoader() + ", where as it should've been " + slClass.getClassLoader());
                }
                LauncherConfiguration config = (LauncherConfiguration) cfgField.get(instance);
                ExtensionManager extensions = (ExtensionManager) extField.get(instance);
                Starloader.instance = new Starloader(config, extensions);
            } catch (Exception e) {
                throw new IllegalStateException("This class should be loaded by the root classloader!", e);
            }
        }
    }
}
