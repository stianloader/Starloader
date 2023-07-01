package de.geolykt.starloader.launcher;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.Set;

import org.json.JSONException;
import org.json.JSONObject;
import org.slf4j.LoggerFactory;

import net.minestom.server.extras.selfmodification.HierarchyClassLoader;
import net.minestom.server.extras.selfmodification.MinestomRootClassLoader;

import de.geolykt.micromixin.MixinTransformer;
import de.geolykt.micromixin.supertypes.ClassWrapperPool;

public class CLILauncher {

    private static Set<Path> getPaths(JSONObject jsonConfig) {

        Set<Path> extraPaths = new HashSet<>();

        //
        for (Object o : jsonConfig.getJSONArray("classPath")) {
            extraPaths.add(Paths.get(o.toString()).toAbsolutePath());
        }
        URI launcherURI;
        try {
            launcherURI = CLILauncher.class.getProtectionDomain().getCodeSource().getLocation().toURI();
        } catch (URISyntaxException e) {
            e.printStackTrace();
            return extraPaths;
        }
        Path launcherPath = Paths.get(launcherURI);
        extraPaths.remove(launcherPath);
        return extraPaths;
    }

    public static void main(String[] args) {
        Set<Path> bootPaths = null;
        try {
            String read = new String(Files.readAllBytes(Paths.get("config.json")), StandardCharsets.UTF_8);
            JSONObject json = new JSONObject(read);
            bootPaths = getPaths(json);
        } catch (JSONException | IOException e) {
            e.printStackTrace();
            if (bootPaths == null) {
                bootPaths = new HashSet<>();
            }
        }

        MinestomRootClassLoader cl = MinestomRootClassLoader.getInstance();
        bootPaths.forEach(p -> {
            try {
                cl.addURL(p.toUri().toURL());
            } catch (MalformedURLException e) {
                e.printStackTrace();
            }
        });

        // Start mixins & load extensions
        MixinBytecodeProvider provider = new MixinBytecodeProvider();
        ClassWrapperPool cwPool = new ClassWrapperPool();
        cwPool.addProvider(provider);
        MixinTransformer<HierarchyClassLoader> transformer = new MixinTransformer<>(provider, cwPool);
        cl.addTransformer(new ASMMixinTransformer(transformer));
        // ensure extensions are loaded when starting the server
        try {
            Class<?> slClass = cl.loadClass("de.geolykt.starloader.Starloader");
            LauncherConfiguration preferences = new LauncherConfiguration(true);
            preferences.setExtensionsFolder(new File("mods"));
            preferences.getExtensionsFolder().mkdir();
            MethodHandles.lookup().findStatic(slClass, "start", MethodType.methodType(void.class, LauncherConfiguration.class)).invokeExact(preferences);
        } catch (Throwable t) {
            t.printStackTrace();
            return;
        }

        // Find & launch main class
        String mainClass = System.getProperty("de.geolykt.starloader.launcher.CLILauncher.mainClass");

        findManifest:
        try {
            if (mainClass != null) {
                break findManifest;
            }

            Enumeration<URL> manifests = cl.getResources("META-INF/MANIFEST.MF");

            URL manifest = null;
            while (manifests.hasMoreElements()) {
                manifest = manifests.nextElement();
            }
            if (manifest == null) {
                throw new IOException("Unable to find jar manifest!");
            }
            try (BufferedReader br = new BufferedReader(new InputStreamReader(manifest.openStream(), StandardCharsets.UTF_8))) {
                for (String ln = br.readLine(); ln != null; ln = br.readLine()) {
                    ln = ln.split("#", 2)[0];
                    if (ln.startsWith("Main-Class:")) {
                        mainClass = ln.split(":", 2)[1].trim();
                        break;
                    }
                }
            } catch (IOException e) {
                throw new IOException("Unable to find jar manifest!", e);
            }
        } catch (IOException t) {
            t.printStackTrace();
        }

        if (mainClass == null) {
            LoggerFactory.getLogger(CLILauncher.class).error("Unable to find main class! Falling back to com.example.Main");
            mainClass = "com.example.Main";
        }

        LoggerFactory.getLogger(CLILauncher.class).info("Starting main class " + mainClass + " with arguments " + Arrays.toString(args));

        try {
            Utils.startMain(cl.loadClass(mainClass), args);
        } catch (Throwable t) {
            t.printStackTrace();
        }
    }
}
