package de.geolykt.starloader.launcher;

import java.io.IOException;
import java.io.InputStream;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.LoggerFactory;

import de.geolykt.starloader.mod.ExtensionPrototype;

/**
 * Collection of static utility methods.
 */
public final class Utils {

   private static final byte[] SHARED_DUMMY_ARRAY = new byte[4096];
   private static final boolean LOCAL_LOGS = Boolean.getBoolean("org.stianloader.launcher.Utils.localLogs");

   /**
    * Expand placeholders as defined by {@link ExtensionPrototype#getDefinedProperties()}.
    * This method will recursively replace placeholders.
    *
    * @param sourceResource The resource from which the input string comes from, used when logging unknown keys. Logged as-is,
    * so the {@link Object#toString()} method of the value passed as this argument should be human-readable.
    * @param string The input string which may contain placeholders to expand.
    * @param startIndex The index from which the expansion should start from. When calling this method directly,
    * it usually can be set to 0 to replace the entire string.
    * @param placeholders The placeholders to apply.
    * @return The returned string with all relevant properties being expanded.
    * @since 4.0.0-a20240711
    */
   @NotNull
   @ApiStatus.AvailableSince("4.0.0-a20240711")
   @Contract(pure = true)
   public static final String applyPlaceholders(@NotNull Object sourceResource, @NotNull String string, int startIndex, @NotNull Map<String, String> placeholders) {
       int indexStart = string.indexOf("${", startIndex);
       if (indexStart == -1) {
           return string;
       }
       int indexEnd = string.indexOf('}', indexStart);
       String property = string.substring(indexStart + 2, indexEnd);
       String replacement = placeholders.get(property);
       if (replacement == null) {
           LoggerFactory.getLogger(Utils.class).warn("Could not expand resource '{}': Unknown property (or a null value for said property): '{}'", sourceResource, property);
           return Utils.applyPlaceholders(sourceResource, string, indexEnd, placeholders);
       }

       string = string.substring(0, indexStart) + replacement + string.substring(indexEnd + 1);
       return Utils.applyPlaceholders(sourceResource, string, indexStart, placeholders);
   }

    public static final void fastExhaust(@NotNull InputStream in) throws IOException {
        while (in.read(Utils.SHARED_DUMMY_ARRAY) != -1);
    }

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
        if (Utils.LOCAL_LOGS) {
            Path logDir = Paths.get("logs");
            try {
                if (Files.notExists(logDir)) {
                    Files.createDirectory(logDir);
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
            return logDir;
        }

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

    @Nullable
    public static final URI toCodeSourceURI(@Nullable URL url, @NotNull String internalClassName) {
        if (url == null) {
            return null;
        }

        String urlPath = url.getPath();
        if (urlPath.endsWith(".class")) {
            String urlProtocol = url.getProtocol();
            if (urlProtocol.equals("jar")) {
                int index0 = urlPath.indexOf('!');
                if (index0 >= 0) {
                    try {
                        return new URI(urlPath.substring(0, index0));
                    } catch (URISyntaxException e) {
                        LoggerFactory.getLogger(Utils.class).warn("Unable to assimilate jar-protocol-URL: '{}'", url, e);
                    }
                }
            } else if (urlProtocol.equals("file")) {
                String expectedSuffix = internalClassName.replace('.', '/') + ".class";
                if (urlPath.endsWith(expectedSuffix)) {
                    try {
                        return new URI("file", null, url.getHost(), url.getPort(), urlPath.substring(0, urlPath.length() - expectedSuffix.length()), null, null);
                    } catch (URISyntaxException e) {
                        LoggerFactory.getLogger(Utils.class).warn("Unable to assimilate file-protocol-URL: '{}'", url, e);
                    }
                }
            }
        }

        try {
            return url.toURI();
        } catch (URISyntaxException e) {
            LoggerFactory.getLogger(Utils.class).debug("Cannot convert URL {} to a URI.", url, e);
            return null;
        }
    }

    private Utils() {}
}
