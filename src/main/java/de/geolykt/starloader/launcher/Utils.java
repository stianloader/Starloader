package de.geolykt.starloader.launcher;

import java.io.IOException;
import java.io.InputStream;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.jetbrains.annotations.NotNull;

/**
 * Collection of static utility methods.
 */
public final class Utils {

   private static final byte[] SHARED_DUMMY_ARRAY = new byte[4096];

    /**
     * Obtains the directory where SLL puts its logs.
     *
     * <p>It will first try to set the log directory to the value described by XDG_STATE_HOME, then
     * "%APPDATA%/stianloader", then "${user.home}/.local/state/stianloader" and if it still fails
     * it will put it in the current working directory. It will however only create a single directory
     * in order to accomplish this, so it won't always use the current working directory
     * if less advanced users attempt to use the launcher.
     *
     * @return The directory in which the logs of SLL are stored.
     */
    public static final Path getLogDirectory() {
        // XDG base directory specification: https://specifications.freedesktop.org/basedir-spec/basedir-spec-latest.html
        String xdgStateHome = System.getenv("XDG_STATE_HOME");
        if (xdgStateHome != null && !xdgStateHome.isEmpty()) {
            Path p = Paths.get(xdgStateHome, "stianloader");
            try {
                Files.createDirectories(p);
                return p;
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        String appdataFolder = System.getenv("APPDATA");
        if (appdataFolder != null) {
            Path location = Paths.get(appdataFolder, "stianloader");
            try {
                if (!Files.isDirectory(location)) {
                    Files.createDirectory(location);
                }
            } catch (IOException expected) {
                expected.printStackTrace();
                return Paths.get(".");
            }
            return location;
        } else {
            String userhome = System.getProperty("user.home");
            if (userhome == null) {
                return Paths.get(".");
            }
            Path location = Paths.get(userhome, ".local");
            if (Files.notExists(location)) {
                return Paths.get(".");
            }
            location = location.resolve("state");
            if (Files.notExists(location)) {
                try {
                    if (!Files.isDirectory(location)) {
                        Files.createDirectory(location);
                    }
                } catch (IOException expected) {
                    expected.printStackTrace();
                    return Paths.get(".");
                }
            }
            location = location.resolve("stianloader");
            try {
                if (!Files.isDirectory(location)) {
                    Files.createDirectory(location);
                }
            } catch (IOException expected) {
                expected.printStackTrace();
                return Paths.get(".");
            }
            return location;
        }
    }

    public static final void startMain(Class<?> className, String[] args) {
        try {
            MethodHandles.publicLookup().findStatic(className, "main", MethodType.methodType(void.class, String[].class)).invokeExact(args);
        } catch (Throwable e) {
            throw new RuntimeException("Error while invoking main class!", e);
        }
    }

    public static final void fastExhaust(@NotNull InputStream in) throws IOException {
        while (in.read(Utils.SHARED_DUMMY_ARRAY) != -1);
    }

    /**
     * The constructor.
     * DO NOT CALL THE CONSTRUCTOR.
     */
    private Utils() {
        // Do not construct classes for absolutely no reason at all
        throw new RuntimeException("Didn't the javadoc tell you to NOT call the constructor of this class?");
    }
}
