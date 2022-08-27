package de.geolykt.starloader.launcher;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Method;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.Locale;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongepowered.asm.launch.MixinBootstrap;
import org.spongepowered.asm.mixin.MixinEnvironment.Phase;
import org.spongepowered.asm.mixin.Mixins;

import net.minestom.server.extras.selfmodification.MinestomRootClassLoader;

import de.geolykt.starloader.Starloader;
import de.geolykt.starloader.UnlikelyEventException;
import de.geolykt.starloader.launcher.service.SLMixinService;

/**
 * Collection of static utility methods.
 */
public final class Utils {

    private static final Logger LOGGER = LoggerFactory.getLogger(Starloader.class);

    public static final String OPERATING_SYSTEM = System.getProperty("os.name");

    public static final int STEAM_GALIMULATOR_APPID = 808100;
    public static final String STEAM_GALIMULATOR_APPNAME = "Galimulator";

    public static final String STEAM_WINDOWS_REGISTRY_INSTALL_DIR_KEY = "InstallPath";

    public static final String STEAM_WINDOWS_REGISTRY_KEY = "HKEY_LOCAL_MACHINE\\SOFTWARE\\Wow6432Node\\Valve\\Steam";

    /**
     * Obtains the directory where starloader puts it's logs into,
     * where the configuration is located and where the extension directory
     * is located.
     *
     * <p>It will first try to set the application folder to "%APPDATA%/starloader",
     * then "${user.home}/.local/share/starloader" and if it still fails it will put it in
     * the current working directory. It will however only create a single directory
     * in order to accomplish this, so it won't always use the current working directory
     * if less advanced users attempt to use the launcher.
     *
     * <p>The reason the current working directory is not used outright is that on
     * JPackage'd distributions of the Starloader-launcher this working directory is
     * the user home, which would create a lot of clutter. This is not intended.
     *
     * @return The main application folder for starloader.
     */
    public static final File getApplicationFolder() {
        String appdataFolder = System.getenv("APPDATA");
        if (appdataFolder != null) {
            File f = new File(appdataFolder, "starloader");
            f.mkdir();
            return f;
        } else {
            String userhome = System.getProperty("user.home");
            if (userhome == null) {
                return getCurrentDir();
            }
            File f = new File(userhome, ".local");
            if (!f.exists()) {
                return getCurrentDir();
            }
            f = new File(f, "share");
            if (!f.exists()) {
                return getCurrentDir();
            }
            f = new File(f, "starloader");
            f.mkdir();
            return f;
        }
    }

    public static final String getChecksum(File file) {
        MessageDigest digest;
        try {
            digest = MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
        if (!file.exists()) {
            throw new RuntimeException("Jar was not found!");
        }
        try (DigestInputStream digestStream = new DigestInputStream(new FileInputStream(file), digest)) {
            if (isJava9()) {
                digestStream.readAllBytes(); // This should be considerably faster than the other methods, however only got introduced in Java 9
            } else {
                while (digestStream.read() != -1) {
                    // Empty block; Read all bytes
                }
            }
            digest = digestStream.getMessageDigest();
        } catch (Exception e) {
            throw new RuntimeException("Something went wrong while obtaining the checksum of the galimulator jar.", e);
        }
        StringBuilder result = new StringBuilder();
        for (byte b : digest.digest()) {
            result.append(String.format("%02x", b));
        }
        return result.toString();
    }

    public static final File getCurrentDir() {
        return new File(".");
    }

    public static final File getGameDir(String game) {
        File steamExec = getSteamExecutableDir();
        if (steamExec == null || !steamExec.exists()) {
            if (OPERATING_SYSTEM.toLowerCase(Locale.ROOT).startsWith("win")) {
                steamExec = getOneOfExistingFiles("C:\\Steam\\", "C:\\Program Files (x86)\\Steam\\", "C:\\Program Files\\Steam\\", "D:\\Steam\\", "C:\\Programmes\\Steam\\", "D:\\Programmes\\Steam\\");
            } else {
                // Assuming my install
                String homeDir = System.getProperty("user.home");
                File usrHome = new File(homeDir);
                File steamHome = new File(usrHome, ".steam");
                steamExec = new File(steamHome, "steam");
                if (!steamExec.exists()) {
                    return null;
                }
            }
            if (steamExec == null) {
                return null;
            }
        }
        if (!steamExec.isDirectory()) {
            throw new UnlikelyEventException();
        }
        File appdata = new File(steamExec, "steamapps");
        File common = new File(appdata, "common");
        return new File(common, game);
    }

    public static final File getOneOfExistingFiles(String... paths) {
        for (String path : paths) {
            File file = new File(path);
            if (file.exists()) {
                return file;
            }
        }
        return null;
    }

    public static final File getSteamExecutableDir() {
        if (OPERATING_SYSTEM.toLowerCase(Locale.ROOT).startsWith("win")) {
            String val = readWindowsRegistry(STEAM_WINDOWS_REGISTRY_KEY, STEAM_WINDOWS_REGISTRY_INSTALL_DIR_KEY);
            System.out.println(val);
            if (val == null) {
                return null;
            }
            return new File(val);
        } else {
            // Assuming UNIX, though for real we should check other OSes
            String homeDir = System.getProperty("user.home");
            File usrHome = new File(homeDir);
            File steamHome = new File(usrHome, ".steam");
            if (steamHome.exists()) {
                // some installs have the steam directory located in ~/.steam/debian-installation
                File debianInstall = new File(steamHome, "debian-installation");
                if (debianInstall.exists()) {
                    return debianInstall;
                } else {
                    return new File(steamHome, "steam");
                }
            }
            // Steam folder not located in ~/.steam, checking in ~/.local/share
            File local = new File(usrHome, ".local");
            if (!local.exists()) {
                return null; // Well, we tried...
            }
            File share = new File(local, "share");
            if (!share.exists()) {
                return null;
            }
            return new File(share, "Steam");
        }
    }

    public static final boolean isJava9() {
        try {
            URLClassLoader.getPlatformClassLoader(); // This should throw an error in Java 8 and below
            // I am well aware that this will never throw an error due to Java versions, but it's still a bit of futureproofing
            return true;
        } catch (Throwable e) {
            return false;
        }
    }

    /**
     * Stupid little hack.
     *
     * @param location path in the registry
     * @param key registry key
     * @return registry value or null if not found
     * @author Oleg Ryaboy, based on work by Miguel Enriquez; Made blocking by Geolykt
     */
    public static final String readWindowsRegistry(String location, String key) {
        try {
            // Run reg query, then read it's output
            Process process = Runtime.getRuntime().exec("reg query " + '"' + location + "\" /v " + key);

            process.waitFor();
            InputStream is = process.getInputStream();
            String output = new String(is.readAllBytes(), StandardCharsets.UTF_8);
            is.close();

            if (!output.contains(location) || !output.contains(key)) {
                return null;
            }

            // Parse out the value
            // For me this results in:
            // [, HKEY_LOCAL_MACHINE\SOFTWARE\Wow6432Node\Valve\Steam, InstallPath, REG_SZ, D:\Programmes\Steam]
            String[] parsed = output.split("\\s+");
            return parsed[parsed.length-1];
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    @SuppressWarnings("resource")
    public static final void startGalimulator(String[] args, LauncherConfiguration preferences) {
        if (isJava9()) {
            MinestomRootClassLoader cl = MinestomRootClassLoader.getInstance();
            try {
                cl.addURL(preferences.getTargetJar().toURI().toURL());
            } catch (MalformedURLException e1) {
                throw new RuntimeException("Something went wrong while adding the target jar to the Classpath", e1);
            }
            try {
                if (preferences.hasExtensionsEnabled()) {
                    startMixin(args);
                    cl.addTransformer(new ASMMixinTransformer(SLMixinService.getInstance()));
                    SLMixinService.getInstance().getPhaseConsumer().accept(Phase.PREINIT);
                    // ensure extensions are loaded when starting the server
                    Class<?> slClass = cl.loadClass("de.geolykt.starloader.Starloader");
                    Method init = slClass.getDeclaredMethod("start", LauncherConfiguration.class);
                    init.invoke(null, preferences);
                    SLMixinService.getInstance().getPhaseConsumer().accept(Phase.INIT);
                    SLMixinService.getInstance().getPhaseConsumer().accept(Phase.DEFAULT);
                }

                URL manifest = null;
                Enumeration<URL> manifests = cl.findResources("META-INF/MANIFEST.MF");

                while (manifests.hasMoreElements()) {
                    manifest = manifests.nextElement();
                }

                if (manifest == null) {
                    manifests = cl.getResources("META-INF/MANIFEST.MF");

                    while (manifests.hasMoreElements()) {
                        manifest = manifests.nextElement();
                    }

                    if (manifest == null) {
                        throw new IllegalStateException("Unable to find jar manifest!");
                    }
                }

                String mainClass = null;
                try (BufferedReader br = new BufferedReader(new InputStreamReader(manifest.openStream(), StandardCharsets.UTF_8))) {
                    for (String ln = br.readLine(); ln != null; ln = br.readLine()) {
                        ln = ln.split("#", 2)[0];
                        if (ln.startsWith("Main-Class:")) {
                            mainClass = ln.split(":", 2)[1].trim();
                            break;
                        }
                    }
                } catch (IOException e) {
                    throw new IllegalStateException("Unable to find jar manifest!", e);
                }

                LOGGER.info("Starting main class " + mainClass + " with arguments " + Arrays.toString(args));
                startMain(cl.loadClass(mainClass), args);
            } catch (Exception e1) {
                throw new RuntimeException("Something went wrong while bootstrapping.", e1);
            }
        } else {
            try {
                throw new UnsupportedOperationException("Java 8 is not supported.");
            } catch (SecurityException | IllegalArgumentException e1) {
                throw new RuntimeException("Something went wrong while adding the target jar to the Classpath", e1);
            }
        }
    }

    public static final void startMain(Class<?> className, String[] args) {
        try {
            Method main = className.getDeclaredMethod("main", String[].class);
            main.setAccessible(true);
            // Note to self future : do not attempt to cast the class(es), it won't work well.
            // This means that the array instantiation is intended
            main.invoke(null, new Object[] { args });
        } catch (Exception e) {
            throw new RuntimeException("Error while invoking main class!", e);
        }
    }

    protected static final void startMixin(String[] args) {
        MixinBootstrap.init();
        MixinBootstrap.getPlatform().inject();
        Mixins.getConfigs().forEach(c -> MinestomRootClassLoader.getInstance().protectedPackages.add(c.getConfig().getMixinPackage()));
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
