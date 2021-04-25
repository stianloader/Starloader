package de.geolykt.starloader.launcher;

import java.awt.Dimension;
import java.io.File;
import java.io.FileInputStream;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Locale;

import org.spongepowered.asm.launch.MixinBootstrap;
import org.spongepowered.asm.launch.platform.CommandLineOptions;
import org.spongepowered.asm.mixin.Mixins;

import de.geolykt.starloader.UnlikelyEventException;

import net.minestom.server.extras.selfmodification.MinestomRootClassLoader;
import net.minestom.server.extras.selfmodification.mixins.MixinCodeModifier;
import net.minestom.server.extras.selfmodification.mixins.MixinServiceMinestom;

/**
 * Collection of static utility methods.
 */
public final class Utils {

    /**
     * The constructor.
     * DO NOT CALL THE CONSTRUCTOR.
     */
    private Utils() {
        // Do not construct classes for absolutely no reason at all
    }

    public static final int STEAM_GALIMULATOR_APPID = 808100;
    public static final String STEAM_GALIMULATOR_APPNAME = "Galimulator";

    public static final String OPERATING_SYSTEM = System.getProperty("os.name");

    public static final String STEAM_WINDOWS_REGISTRY_KEY = "HKEY_LOCAL_MACHINE\\SOFTWARE\\Wow6432Node\\Valve\\Steam";
    public static final String STEAM_WINDOWS_REGISTRY_INSTALL_DIR_KEY = "InstallPath";

    public static final HashMap<String, String> VERSIONS = new HashMap<>();

    /**
     * Stupid little hack.
     *
     * @param location path in the registry
     * @param key registry key
     * @return registry value or null if not found
     * @author Oleg Ryaboy, based on work by Miguel Enriquez; Made blocking by Geolykt
     */
    public static final String readWindowsRegistry(String location, String key){
        try {
            // Run reg query, then read output with StreamReader (internal class)
            Process process = Runtime.getRuntime().exec("reg query " + 
                    '"'+ location + "\" /v " + key);

            process.waitFor();
            @SuppressWarnings("resource")
            String output = new String(process.getInputStream().readAllBytes());

            // Output has the following format:
            // \n<Version information>\n\n<key>\t<registry type>\t<value>
            if(!output.contains("\t")){
                    return null;
            }

            // Parse out the value
            String[] parsed = output.split("\t");
            return parsed[parsed.length-1];
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }

    }

    public static final File getSteamExecutableDir() {
        if (OPERATING_SYSTEM.toLowerCase(Locale.ROOT).startsWith("win")) {
            // TODO test
            String val = readWindowsRegistry(STEAM_WINDOWS_REGISTRY_KEY, STEAM_WINDOWS_REGISTRY_INSTALL_DIR_KEY);
            System.out.println(val);
            return new File(val);
        } else {
            // Assuming UNIX, though for real we should check other OSes
            String homeDir = System.getProperty("user.home");
            File usrHome = new File(homeDir);
            File steamHome = new File(usrHome, ".steam");
            return new File(steamHome, "steam");
        }
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

    public static File getGameDir(String game) {
        File steamExec = getSteamExecutableDir();
        if (steamExec == null || !steamExec.exists()) {
            if (OPERATING_SYSTEM.toLowerCase(Locale.ROOT).startsWith("win")) {
                steamExec = getOneOfExistingFiles("C:\\Steam\\", "C:\\Program Files (x86)\\Steam\\",
                        "C:\\Program Files\\Steam\\", "D:\\Steam\\", "C:\\Programmes\\Steam\\", "D:\\Programmes\\Steam\\");
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

    public static void addToCPJ8 (File file) throws ReflectiveOperationException, SecurityException, IllegalArgumentException, MalformedURLException {
        @SuppressWarnings("resource") // The system classloader should not be closed
        URLClassLoader sysloader = (URLClassLoader) ClassLoader.getSystemClassLoader();
        Method method = URLClassLoader.class.getDeclaredMethod("addURL", new Class[]{URL.class});
        method.invoke(sysloader, new Object[]{file.toURI().toURL()});
    }

    public static String getChecksum(File file) {
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

    public static final boolean isJava9() {
        try {
            URLClassLoader.getPlatformClassLoader(); // This should throw an error in Java 8 and below
            // I am well aware that this will never throw an error due to Java versions, but it's stil a bit of futureproofing
            return true;
        } catch (Throwable e) {
            return false;
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

    @SuppressWarnings("resource")
    protected static final void startGalimulator(String[] args, LauncherConfiguration preferences) {
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
                    cl.addCodeModifier(new MixinCodeModifier());
                    MixinServiceMinestom.gotoPreinitPhase();
                    // ensure extensions are loaded when starting the server
                    Class<?> serverClass = cl.loadClass("de.geolykt.starloader.Starloader");
                    Method init = serverClass.getMethod("init");
                    init.invoke(null);
                    MixinServiceMinestom.gotoInitPhase();
                    MixinServiceMinestom.gotoDefaultPhase();
                }

                startMain(cl.loadClass("com.example.Main"), args);
            } catch (Exception e1) {
                throw new RuntimeException("Something went wrong while bootstrapping.", e1);
            }
        } else {
            try {
                addToCPJ8(preferences.getTargetJar());
            } catch (SecurityException | IllegalArgumentException | MalformedURLException
                    | ReflectiveOperationException e1) {
                throw new RuntimeException("Something went wrong while adding the target jar to the Classpath", e1);
            }
            try {
                startMain(Class.forName("com.example.Main"), args);
            } catch (ClassNotFoundException e1) {
                throw new RuntimeException("Unable to locate Main class!", e1);
            }
        }
    }

    protected static final void startMixin(String[] args) throws NoSuchMethodException, InvocationTargetException, IllegalAccessException {
        // hacks required to pass custom arguments
        Method start = MixinBootstrap.class.getDeclaredMethod("start");
        start.setAccessible(true);
        if (!((boolean)start.invoke(null))) {
            return;
        }

        Method doInit = MixinBootstrap.class.getDeclaredMethod("doInit", CommandLineOptions.class);
        doInit.setAccessible(true);
        doInit.invoke(null, CommandLineOptions.ofArgs(Arrays.asList(args)));

        MixinBootstrap.getPlatform().inject();
        Mixins.getConfigs().forEach(c -> MinestomRootClassLoader.getInstance().protectedPackages.add(c.getConfig().getMixinPackage()));
    }

    public static final Dimension combineLargest(Dimension original, Dimension newer) {
        return new Dimension(Math.max(original.width, newer.width), Math.max(original.height, newer.height));
    }

    static {
        // TODO add more jars to this collection and check "pure guess" jars
        // It would also be interesting if steam/itch provides the checksums for this file, could we check or not?

        // This is a pure guess based on the windows jar that I own and then 4.7 linux jar that I own
        VERSIONS.put("f8ea33e66efbefda91a4c24f8f44b45700e27a0ad0765eeec049f77b6b7307cc", "4.5-stable-linux");
        VERSIONS.put("25a7738ff8a137fc1d1e668535b4ca3464609aba4e45eaa276a698f364add666", "4.5-stable-windows");
        VERSIONS.put("d0f0bc784e1596a38c61c007b077aebb366146b878f692fe15fec850504adb0f", "4.7-stable-linux");
        VERSIONS.put("b659d3fd10bf03d90bfa3142614e7c70793d9fc184e1cfcc39f1535e726d7d08", "4.7-stable-windows");
        VERSIONS.put("dbaff4dbb9d9289fc0424f7d538fe48fe87b6bb2ad50cbb52f443e1d7ef670ab", "4.8-beta-linux"); // There was only one beta release for 4.8 (the other was the stable release)
        // Galimulator 4.8 was accidentally released in a somewhat Platform-independent state, that means the same jar should run on all plattforms
        VERSIONS.put("a09045718ca85933c7a53461cc313602dd803dbd773dfef2b72044ee8f57b156", "4.8-stable-linux (contains Windows natives)");
    }

    public static File getCurrentDir() {
        return new File(".");
    }
}
