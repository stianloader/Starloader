package de.geolykt.starloader.launcher;

import java.util.Locale;

public enum SystemOS {

    WINDOWS("https://github.com/adoptium/temurin17-binaries/releases/download/jdk-17.0.3%2B7/OpenJDK17U-jdk_x64_windows_hotspot_17.0.3_7.zip"),
    LINUX("https://github.com/adoptium/temurin17-binaries/releases/download/jdk-17.0.3%2B7/OpenJDK17U-jdk_x64_linux_hotspot_17.0.3_7.tar.gz"),
    MAC(null);

    private static final SystemOS CURRENT;
    private final String jdkDownloadPath;

    private SystemOS(String downloadPath) {
        this.jdkDownloadPath = downloadPath;
    }

    public static SystemOS getCurrentOS() {
        return CURRENT;
    }

    public String getJDKDownloadPath() {
        return jdkDownloadPath;
    }

    static {
        String osname = System.getProperty("os.name").toLowerCase(Locale.ROOT);
        if (osname.contains("win")) {
            CURRENT = WINDOWS;
        } else if (osname.contains("osx") || osname.contains("mac")) {
            CURRENT = MAC;
        } else {
            CURRENT = LINUX;
        }
    }
}
