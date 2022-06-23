package de.geolykt.starloader.launcher;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.lang.reflect.Method;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.Set;

import org.json.JSONException;
import org.json.JSONObject;
import org.slf4j.LoggerFactory;
import org.spongepowered.asm.mixin.MixinEnvironment.Phase;

import net.minestom.server.extras.selfmodification.MinestomRootClassLoader;

import de.geolykt.starloader.launcher.service.SLMixinService;

public class CLILauncher {

    private static Set<Path> getPaths(JSONObject jsonConfig) {

        Set<Path> extraPaths = new HashSet<>();

        //
        for (Object o : jsonConfig.getJSONArray("classPath")) {
            extraPaths.add(Path.of(o.toString()).toAbsolutePath());
        }
        URI launcherURI;
        try {
            launcherURI = CLILauncher.class.getProtectionDomain().getCodeSource().getLocation().toURI();
        } catch (URISyntaxException e) {
            e.printStackTrace();
            return extraPaths;
        }
        Path launcherPath = Path.of(launcherURI);
        extraPaths.remove(launcherPath);
        return extraPaths;
    }

    public static void main(String[] args) {
        Set<Path> bootPaths = null;
        try {
            JSONObject json = new JSONObject(Files.readString(Path.of("config.json"), StandardCharsets.UTF_8));
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
        Utils.startMixin(args);
        cl.addTransformer(new ASMMixinTransformer(SLMixinService.getInstance()));
        SLMixinService.getInstance().getPhaseConsumer().accept(Phase.PREINIT);
        // ensure extensions are loaded when starting the server
        try {
            Class<?> slClass = cl.loadClass("de.geolykt.starloader.Starloader");
            Method init = slClass.getDeclaredMethod("start", LauncherConfiguration.class);

            LauncherConfiguration preferences = new LauncherConfiguration(true);

            preferences.setExtensionsFolder(new File("mods"));
            preferences.getExtensionsFolder().mkdir();

            init.invoke(null, preferences);
        } catch (Exception e) {
            e.printStackTrace();
            return;
        }

        SLMixinService.getInstance().getPhaseConsumer().accept(Phase.INIT);
        SLMixinService.getInstance().getPhaseConsumer().accept(Phase.DEFAULT);

        // Find & launch main class
        try {
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

            LoggerFactory.getLogger(CLILauncher.class).info("Starting main class " + mainClass + " with arguments " + Arrays.toString(args));
            Utils.startMain(cl.loadClass(mainClass), args);
        } catch (Throwable t) {
            t.printStackTrace();
        }
    }
}
