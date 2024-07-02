package de.geolykt.starloader.util;

import de.geolykt.starloader.launcher.Utils;

import ch.qos.logback.core.rolling.TimeBasedRollingPolicy;

public class XDGAwareRollingPolicy<E> extends TimeBasedRollingPolicy<E> {
    @Override
    public void start() {
        this.setFileNamePattern(Utils.getLogDirectory().toAbsolutePath().toString() + "/" + this.getFileNamePattern());
        super.start();
    }
}
