package de.geolykt.starloader.launcher;

import org.slf4j.LoggerFactory;
import org.stianloader.micromixin.transform.api.MixinLoggingFacade;

import net.minestom.server.extras.selfmodification.MinestomRootClassLoader;

class SLF4JLoggingFacade implements MixinLoggingFacade {

    @Override
    public void debug(Class<?> clazz, String message, Object... args) {
        if (MinestomRootClassLoader.getInstance().isThreadLoggingClassloadingFailures()) {
            LoggerFactory.getLogger(clazz).trace(message, args);
        }
    }

    @Override
    public void error(Class<?> clazz, String message, Object... args) {
        if (MinestomRootClassLoader.getInstance().isThreadLoggingClassloadingFailures()) {
            LoggerFactory.getLogger(clazz).error(message, args);
        }
    }

    @Override
    public void info(Class<?> clazz, String message, Object... args) {
        if (MinestomRootClassLoader.getInstance().isThreadLoggingClassloadingFailures()) {
            LoggerFactory.getLogger(clazz).info(message, args);
        }
    }

    @Override
    public void warn(Class<?> clazz, String message, Object... args) {
        if (MinestomRootClassLoader.getInstance().isThreadLoggingClassloadingFailures()) {
            LoggerFactory.getLogger(clazz).warn(message, args);
        }
    }
}
