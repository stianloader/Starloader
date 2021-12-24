package de.geolykt.starloader.launcher;

import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.reflect.InvocationTargetException;
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
import java.util.HashMap;
import java.util.Locale;

import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.Popup;
import javax.swing.PopupFactory;

import org.spongepowered.asm.launch.MixinBootstrap;
import org.spongepowered.asm.launch.platform.CommandLineOptions;
import org.spongepowered.asm.mixin.MixinEnvironment.Phase;
import org.spongepowered.asm.mixin.Mixins;

import net.minestom.server.extras.selfmodification.MinestomRootClassLoader;

import de.geolykt.starloader.UnlikelyEventException;
import de.geolykt.starloader.launcher.components.MouseClickListener;
import de.geolykt.starloader.launcher.service.SLMixinService;
import de.geolykt.starloader.util.Version;
import de.geolykt.starloader.util.Version.Stabillity;

/**
 * Collection of static utility methods.
 */
public final class Utils {

    public static final String OPERATING_SYSTEM = System.getProperty("os.name");

    public static final int STEAM_GALIMULATOR_APPID = 808100;
    public static final String STEAM_GALIMULATOR_APPNAME = "Galimulator";

    public static final String STEAM_WINDOWS_REGISTRY_INSTALL_DIR_KEY = "InstallPath";

    public static final String STEAM_WINDOWS_REGISTRY_KEY = "HKEY_LOCAL_MACHINE\\SOFTWARE\\Wow6432Node\\Valve\\Steam";
    public static final HashMap<String, Version> VERSIONS = new HashMap<>();

    static {
        // TODO add more jars to this collection and check "pure guess" jars
        // It would also be interesting if steam/itch provides the checksums for this file, could we check or not?

        // 4.5 Linux checksum is a pure guess, while all other windows checksums (except for 4.5) are also pure guesses
        VERSIONS.put("f8ea33e66efbefda91a4c24f8f44b45700e27a0ad0765eeec049f77b6b7307cc", new Version(4, 5, 0, "linux", Stabillity.STABLE));
        VERSIONS.put("25a7738ff8a137fc1d1e668535b4ca3464609aba4e45eaa276a698f364add666", new Version(4, 5, 0, "windows", Stabillity.STABLE));
        VERSIONS.put("d0f0bc784e1596a38c61c007b077aebb366146b878f692fe15fec850504adb0f", new Version(4, 7, 0, "linux", Stabillity.STABLE));
        VERSIONS.put("b659d3fd10bf03d90bfa3142614e7c70793d9fc184e1cfcc39f1535e726d7d08", new Version(4, 7, 0, "windows", Stabillity.STABLE));
        VERSIONS.put("dbaff4dbb9d9289fc0424f7d538fe48fe87b6bb2ad50cbb52f443e1d7ef670ab", new Version(4, 8, 0, "linux", Stabillity.BETA));
        // 4.8 was accidentally released in a somewhat Platform-independent state, that means the same jar should run on all plattforms
        VERSIONS.put("a09045718ca85933c7a53461cc313602dd803dbd773dfef2b72044ee8f57b156", new Version(4, 8, 0, "linux", "contains Windows natives", Stabillity.STABLE));
        VERSIONS.put("6a8ffa84adafe1019a8359ca69dc9ee75f4a3a6ce1da916bf130976639f82fea", new Version(4, 9, 0, "linux", Stabillity.BETA));
        VERSIONS.put("49c1e88149b6ee286772805b286529b20a5f77aa2310f386b77d7ec3110bab50", new Version(4, 9, 1, "linux", Stabillity.BETA));
        VERSIONS.put("5b0dbb545b157b43e9fa1f85cfe5f6afa8203b61567fed6404d7172a242584d6", new Version(4, 9, 2, "linux", Stabillity.BETA));
        VERSIONS.put("d41575068df6b7139115b1d216c5bf047d192c214b66b88d5eef00aeccf1367a", new Version(4, 9, 3, "linux", Stabillity.BETA));
        VERSIONS.put("addad321401c5e711b3267acbc4c89fee8e7767e2a9507ab13a98b9e2306a2d1", new Version(4, 9, 4, "linux", Stabillity.BETA));
        VERSIONS.put("b252fb1587f622246dc46846113c2c023a04f7262e9c621aed7039bb85046146", new Version(4, 9, 5, "linux", Stabillity.BETA));
        VERSIONS.put("2769105a9ee6b1519daf5d355598dcc49d08b09479ede7fb48011bd3dcd50435", new Version(4, 9, 6, "linux", Stabillity.BETA));
        VERSIONS.put("e31a2a169bf63836007f55b3078d4c28d3196890ab3987cf1d9135a14e2903d1", new Version(4, 9, 0, "linux", Stabillity.STABLE));
        VERSIONS.put("9e40f7eb4d71205dc6a31b6765fa6f76678aceb64e0e55a72abe915a1d2c2664", new Version(4, 9, 0, "windows", Stabillity.STABLE));
        VERSIONS.put("fac772c5b99e5cd7e1724eabe6d5ce9ff188c1db38d0516241d644f6b97e7768", new Version(4, 10, 0, "linux", Stabillity.ALPHA));
        VERSIONS.put("9e40f7eb4d71205dc6a31b6765fa6f76678aceb64e0e55a72abe915a1d2c2664", new Version(4, 10, 1, "linux", Stabillity.ALPHA));
    }

    public static final Dimension combineLargest(Dimension original, Dimension newer, Dimension max) {
        int width = Math.min(Math.max(original.width, newer.width), max.width);
        int height = Math.min(Math.max(original.height, newer.height), max.height);
        return new Dimension(width, height);
    }

    public static final Popup createPopup(Component owner, Component contents, boolean darkerBackground) {
        if (darkerBackground) {
            contents.setBackground(Color.GRAY);
            if (contents instanceof JComponent) {
                ((JComponent) contents).setOpaque(true);
            }
        }
        int hwowner = owner.getWidth() / 2;
        int hhowner = owner.getHeight() / 2;
        int hwcontent = (int) contents.getPreferredSize().getWidth() / 2;
        int hhcontent = (int) contents.getPreferredSize().getHeight() / 2;
        int x = owner.getX() + Math.max(hwowner - hwcontent, 0);
        int y = owner.getY() + Math.max(hhowner - hhcontent, 0);
        return PopupFactory.getSharedInstance().getPopup(owner, contents, x, y);
    }

    public static final File getApplicationFolder() {
        String appdataFolder = System.getenv("APPDATA");
        if (appdataFolder != null) {
            return new File(appdataFolder, "starloader");
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

    public static final void reportError(Component parent, Throwable t) {
        JPanel popupPanel = new JPanel();
        popupPanel.setLayout(new BoxLayout(popupPanel, BoxLayout.Y_AXIS));
        popupPanel.add(new JLabel("An error has occured!"));
        StringWriter swriter = new StringWriter();
        PrintWriter writer = new PrintWriter(swriter);
        t.printStackTrace(writer);
        swriter.toString().lines().forEachOrdered(ln -> {
            popupPanel.add(new JLabel(ln));
        });

        JButton popupButton = new JButton("Ok");
        popupPanel.add(popupButton);
        Popup popup = createPopup(parent, popupPanel, true);
        popupButton.addMouseListener(new MouseClickListener(popup::hide));
        popup.show();
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
                    cl.addTransformer(new ASMMixinTransformer(SLMixinService.getInstance()));
                    SLMixinService.getInstance().getPhaseConsumer().accept(Phase.PREINIT);
                    // ensure extensions are loaded when starting the server
                    Class<?> slClass = cl.loadClass("de.geolykt.starloader.Starloader");
                    Method init = slClass.getDeclaredMethod("start", LauncherConfiguration.class);
                    init.invoke(null, preferences);
                    SLMixinService.getInstance().getPhaseConsumer().accept(Phase.INIT);
                    SLMixinService.getInstance().getPhaseConsumer().accept(Phase.DEFAULT);
                }
                Enumeration<URL> manifests = cl.getResources("META-INF/MANIFEST.MF");

                URL manifest = null;
                while (manifests.hasMoreElements()) {
                    manifest = manifests.nextElement();
                }
                if (manifest == null) {
                    throw new IllegalStateException("Unable to find jar manifest!");
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

    protected static final void startMixin(String[] args) throws NoSuchMethodException, InvocationTargetException, IllegalAccessException {
        MixinBootstrap.init(CommandLineOptions.ofArgs(Arrays.asList(args)));
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
