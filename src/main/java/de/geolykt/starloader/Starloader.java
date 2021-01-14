package de.geolykt.starloader;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.minestom.server.extensions.Extension;
import net.minestom.server.extensions.ExtensionManager;

public final class Starloader {

    private static final Logger LOGGER = LoggerFactory.getLogger(Starloader.class);

    private static Starloader instance;

    private static ExtensionManager extensions;

    private Starloader() {}

    public static void init() {
        if (instance != null) {
            throw new IllegalStateException("Starloader initialized twice!");
        }
        LOGGER.info("Java version: {}", System.getProperty("java.version"));
        instance = new Starloader();
        extensions = new ExtensionManager();
        extensions.loadExtensions();
        long start = System.currentTimeMillis();
        extensions.getExtensions().forEach(Extension::preInitialize);
        extensions.getExtensions().forEach(Extension::initialize);
        extensions.getExtensions().forEach(Extension::postInitialize);
        LOGGER.info("All Extensions initialized within {}ms", (System.currentTimeMillis() - start));
    }
}
