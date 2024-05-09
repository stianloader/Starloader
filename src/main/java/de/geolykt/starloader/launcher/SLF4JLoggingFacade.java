package de.geolykt.starloader.launcher;

import org.slf4j.LoggerFactory;
import org.stianloader.micromixin.transform.api.MixinLoggingFacade;

class SLF4JLoggingFacade implements MixinLoggingFacade {

    @Override
    public void debug(Class<?> clazz, String message, Object... args) {
        LoggerFactory.getLogger(clazz).debug(message, args);
    }

    @Override
    public void error(Class<?> clazz, String message, Object... args) {
        LoggerFactory.getLogger(clazz).error(message, args);
    }

    @Override
    public void info(Class<?> clazz, String message, Object... args) {
        LoggerFactory.getLogger(clazz).info(message, args);
    }

    @Override
    public void warn(Class<?> clazz, String message, Object... args) {
        LoggerFactory.getLogger(clazz).warn(message, args);
    }
}
