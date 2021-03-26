package de.geolykt.starloader.launcher;

import java.awt.GridLayout;
import java.io.File;
import java.io.FileInputStream;
import java.net.URLClassLoader;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashMap;

import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.WindowConstants;

public class StarloaderLauncher {

    public static final HashMap<String, String> VERSIONS = new HashMap<>();
    protected static JFrame preloaderFrame;
    private static JLabel galVerLabel;
    public static String galVersion = "UNKNOWN";

    public static boolean isJava9() {
        try {
            URLClassLoader.getPlatformClassLoader(); // This should throw an error in Java 8 and below
            // I am well aware that this will never throw an error due to Java versions, but it's stil a bit of futureproofing
            return true;
        } catch (Throwable e) {
            return false;
        }
    }

    public static String getChecksum() {
        MessageDigest digest;
        try {
            digest = MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
        if (!new File("jar").isDirectory()) {
            throw new RuntimeException("Jar directory was not found. Are you launching this application in the correct directory?");
        }
        File jar = new File("jar/galimulator-desktop.jar");
        if (!jar.exists()) {
            throw new RuntimeException("Galimulator jar was not found!");
        }
        try (DigestInputStream digestStream = new DigestInputStream(new FileInputStream(jar), digest)) {
            while (digestStream.read() != -1) {
                // Empty block; Read all bytes
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

    public static void main(String[] args) {
        String infoNotice = "Using Java version " + System.getProperty("java.version");
        boolean mixinSupport = isJava9();
        showGUI(infoNotice, mixinSupport);
        System.out.println("Computing checksum version. This process takes a few seconds. Please wait...");
        galVersion = VERSIONS.getOrDefault(getChecksum(), "UNKNOWN");
        galVerLabel.setText("Detected galimulator version: " + galVersion);
        if (preloaderFrame != null) {
            preloaderFrame.pack();
        }
    }

    public static void showGUI(String infoMessage, boolean mixinsSupported) {
        preloaderFrame = new JFrame("Starloader Launcher");
        JPanel panel = new JPanel();
        panel.add(new JLabel(infoMessage));
        panel.add(galVerLabel = new JLabel("Computing checksum"));

        JButton withoutModding = new JButton("Start without mod support");
        withoutModding.addMouseListener(new SLMouseListener(false));
        panel.add(withoutModding);

        if (mixinsSupported) {
            JButton withModding = new JButton("Start with mod support");
            withModding.addMouseListener(new SLMouseListener(true));
            panel.add(withModding);
        } else {
            panel.add(new JLabel("Mod support requires Java 11+"));
        }
        panel.setLayout(new GridLayout(2, 2));

        preloaderFrame.add(panel);
        preloaderFrame.pack();
        preloaderFrame.setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
        preloaderFrame.setVisible(true);
    }

    static {
        VERSIONS.put("d0f0bc784e1596a38c61c007b077aebb366146b878f692fe15fec850504adb0f", "4.7-stable-linux");
        VERSIONS.put("dbaff4dbb9d9289fc0424f7d538fe48fe87b6bb2ad50cbb52f443e1d7ef670ab", "4.8-beta.1-linux");
        // Beta 2 is the stable release
//      VERSIONS.put("0618dae3a45d083da2a9f56ff67659c74d0547326588f37fc79d3a7febe25ccc", "4.8-beta.2-common");
        // Galimulator 4.8 was accidentally released in a somewhat Platform-independent state, that means the same jar should run on all plattforms
        VERSIONS.put("a09045718ca85933c7a53461cc313602dd803dbd773dfef2b72044ee8f57b156", "4.8-stable-winux");
    }
}
