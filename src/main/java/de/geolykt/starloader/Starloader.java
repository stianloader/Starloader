package de.geolykt.starloader;

import java.io.File;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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

    private void start() {
        extensions.loadExtensions(config.getExtensionList());
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

    public static File getGalimulatorJar() {
        return instance.config.getTargetJar();
    }

    public static File getExtensionDir() {
        return instance.config.getExtensionsFolder();
    }

    public static File getDataDir() {
        return instance.config.getDataFolder();
    }
}
