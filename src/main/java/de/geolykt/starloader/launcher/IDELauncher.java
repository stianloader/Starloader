package de.geolykt.starloader.launcher;

import java.io.IOException;
import java.io.Reader;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import org.json.JSONArray;
import org.json.JSONException;
import org.slf4j.LoggerFactory;
import org.spongepowered.asm.mixin.MixinEnvironment.Phase;

import com.llamalad7.mixinextras.MixinExtrasBootstrap;

import net.minestom.server.extras.selfmodification.MinestomRootClassLoader;

import de.geolykt.starloader.launcher.service.SLMixinService;
import de.geolykt.starloader.mod.DirectoryExtensionPrototypeList;
import de.geolykt.starloader.mod.ExtensionPrototype;
import de.geolykt.starloader.mod.NamedExtensionPrototype;
import de.geolykt.starloader.transformers.StarplaneAnnotationsInlineTransformer;
import de.geolykt.starloader.util.JavaInterop;

/**
 * An entrypoint that is meant for debugging SLL mods within an IDE.
 * As such it heavily relies on system properties in order to work. The following properties
 * are used by this class:
 *
 * <ul>
 *  <li><b>de.geolykt.starloader.launcher.CLILauncher.mainClass</b>: The main class to run after the launcher initialized fully (for galimulator it is com.example.Main).</li>
 *  <li><b>de.geolykt.starloader.launcher.IDELauncher.bootURLs</b>: A JSON-array of URLs to add to the root classloader.</li>
 *  <li><b>de.geolykt.starloader.launcher.IDELauncher.modURLs</b>: A JSON-array of JSON-arrays that specify the URLs used for each mod. That is each array is it's own mod "unit" and may point to a directory or a JAR-file. Mods from the specified mod directory will also be added, should the mod directory be defined via a system property.</li>
 *  <li><b>de.geolykt.starloader.launcher.IDELauncher.modDirectory</b>: Fully qualified path to the mod directory to use.</li>
 *  <li><b>de.geolykt.starloader.launcher.IDELauncher.inlineStarplaneAnnotations</b>: Whether the {@link StarplaneAnnotationsInlineTransformer} should be used.</li>
 *  <li><b>org.stianloader.sll.IDELauncher.propertyExpansionSource</b> (optional): The path to a .properties file from which property expansions within the extension.json file should occur. Only affects mods declared via the 'modURLs' system property.</li>
 * </ul>
 *
 * @since 4.0.0
 */
public class IDELauncher {

    public static void main(String[] args) {
        String mainClass = System.getProperty("de.geolykt.starloader.launcher.CLILauncher.mainClass");
        if (mainClass == null) {
            LoggerFactory.getLogger(IDELauncher.class).warn("Main class not set! Falling back to com.example.Main");
            mainClass = "com.example.Main";
        }
        String modDirectory = System.getProperty("de.geolykt.starloader.launcher.IDELauncher.modDirectory");
        String modURLs = System.getProperty("de.geolykt.starloader.launcher.IDELauncher.modURLs");
        if (modURLs == null) {
            if (modDirectory == null) {
                LoggerFactory.getLogger(IDELauncher.class).error("Unable to find the URLs of mods. Cannot proceed!");
            } else {
                LoggerFactory.getLogger(IDELauncher.class).warn("Unable to find the URLs of mods.");
            }
        } else if (modDirectory == null) {
            LoggerFactory.getLogger(IDELauncher.class).warn("Extension directory undefined.");
        }
        String bootURLs = System.getProperty("de.geolykt.starloader.launcher.IDELauncher.bootURLs");
        if (bootURLs == null) {
            LoggerFactory.getLogger(IDELauncher.class).error("Unable to find the URLs that need to be added to the root classloader. Cannot proceed!");
        }
        if ((modURLs == null && modDirectory == null) || bootURLs == null) {
            throw new IllegalStateException("The modURLs and/or the bootURLs system property is not set.");
        }
        boolean inlineSPAnnotations = Boolean.getBoolean("de.geolykt.starloader.launcher.IDELauncher.inlineStarplaneAnnotations");

        List<URL> bootPaths = new ArrayList<>();
        List<List<URL>> mods = new ArrayList<>();
        Map<String, String> expansionProperties = null;

        readExpansionProperties: {
            String expansionPropertiesPath = System.getProperty("org.stianloader.sll.IDELauncher.propertyExpansionSource");
            if (expansionPropertiesPath == null) {
                break readExpansionProperties;
            }

            Path propertiesPath = Paths.get(expansionPropertiesPath);
            if (Files.notExists(propertiesPath)) {
                LoggerFactory.getLogger(IDELauncher.class).error("The propertyExpansionSource system property points to a non-existent file: '{}'", propertiesPath);
                break readExpansionProperties;
            }

            Properties properties = new Properties();
            try (Reader reader = Files.newBufferedReader(propertiesPath, StandardCharsets.UTF_8)) {
                properties.load(reader);
            } catch (IOException e) {
                LoggerFactory.getLogger(IDELauncher.class).error("Cannot read properties clared by the propertyExpansionSource system property defined as path '{}'", propertiesPath, e);
                break readExpansionProperties;
            }

            expansionProperties = new HashMap<>();
            for (Map.Entry<Object, Object> entry : properties.entrySet()) {
                String value = entry.getValue() == null ? null : entry.getValue().toString();
                expansionProperties.put(entry.getKey().toString(), value);
            }
        }

        try {
            for (Object o : new JSONArray(bootURLs)) {
                if (!(o instanceof String)) {
                    throw new IllegalStateException("Encountered invalid object in boot URL property: " + o);
                }
                try {
                    bootPaths.add(new URL((String) o));
                } catch (MalformedURLException e) {
                    LoggerFactory.getLogger(IDELauncher.class).error("Invalid URL " + o, e);
                    throw new IllegalStateException("Encountered invalid URL in boot URL property: " + o, e);
                }
            }
        } catch (JSONException e) {
            LoggerFactory.getLogger(IDELauncher.class).error("Invalid bootURLs system property: {}", bootURLs, e);
        }

        if (modURLs != null) {
            for (Object o0 : new JSONArray(modURLs)) {
                if (!(o0 instanceof JSONArray)) {
                    LoggerFactory.getLogger(IDELauncher.class).error("Invalid mod {}", o0);
                    throw new IllegalStateException("Encountered invalid object in mods URL property (it should be an array): " + o0);
                }
                List<URL> mod = new ArrayList<>();
                for (Object o : (JSONArray) o0) {
                    if (!(o instanceof String)) {
                        LoggerFactory.getLogger(IDELauncher.class).error("Invalid URL {}", o);
                        throw new IllegalStateException("Invalid URL: " + o);
                    }
                    try {
                        mod.add(new URL((String) o));
                    } catch (MalformedURLException e) {
                        LoggerFactory.getLogger(IDELauncher.class).error("Invalid URL {}", o);
                        throw new IllegalStateException("Encountered invalid URL in boot URL property: " + o, e);
                    }
                }
                mods.add(mod);
            }
        }

        List<ExtensionPrototype> prototypes = new ArrayList<>();
        for (List<URL> mod : mods) {
            prototypes.add(new ExtensionPrototype(mod, true, expansionProperties));
        }

        Path modDirectoryPath = Paths.get(modDirectory == null ? "mods" : modDirectory);
        if (!Files.notExists(modDirectoryPath)) {
            prototypes.addAll(new DirectoryExtensionPrototypeList(modDirectoryPath.toFile()));
        }

        LoggerFactory.getLogger(IDELauncher.class).info("Using prototypes from following sources:");
        prototypes.forEach((prototype) -> {
            prototype.enabled = true;
            if (prototype instanceof NamedExtensionPrototype) {
                NamedExtensionPrototype namedPrototype = (NamedExtensionPrototype) prototype;
                LoggerFactory.getLogger(IDELauncher.class).info("- {} v{} (loaded from {})", namedPrototype.name, namedPrototype.version, namedPrototype.originURLs);
            } else {
                LoggerFactory.getLogger(IDELauncher.class).info("- {}", prototype.originURLs);
            }
        });

        MinestomRootClassLoader cl = MinestomRootClassLoader.getInstance();
        bootPaths.forEach(cl::addURL);

        if (inlineSPAnnotations) {
            LoggerFactory.getLogger(IDELauncher.class).info("Making use of the StarplaneAnnotationsInlineTransformer.");
            cl.addASMTransformer(new StarplaneAnnotationsInlineTransformer());
        }

        // Start mixins & load extensions
        Utils.startMixin(args);
        cl.addASMTransformer(new ASMMixinTransformer(SLMixinService.getInstance()));
        MixinExtrasBootstrap.init(); // The MixinExtras bootstrap MUST be initialized after the ASM transformer
        SLMixinService.getInstance().getPhaseConsumer().accept(Phase.PREINIT);

        // ensure extensions are loaded when starting the server
        try {
            Class<?> slClass = cl.loadClass("de.geolykt.starloader.Starloader");
            MethodHandles.lookup().findStatic(slClass, "start", MethodType.methodType(void.class, List.class, Path.class)).invokeExact(prototypes, modDirectoryPath.toAbsolutePath());
        } catch (Throwable t) {
            t.printStackTrace();
            return;
        }

        SLMixinService.getInstance().getPhaseConsumer().accept(Phase.INIT);
        SLMixinService.getInstance().getPhaseConsumer().accept(Phase.DEFAULT);
        LoggerFactory.getLogger(IDELauncher.class).info("Starting main class " + mainClass + " with arguments " + Arrays.toString(args));

        try {
            Class<?> mainClassInstance = cl.loadClass(mainClass);
            if (mainClassInstance.getClassLoader() != cl) {
                LoggerFactory.getLogger(IDELauncher.class).warn("Main class '{}' loaded by wrong Classloader '{}', expected it to be loaded by '{}'. Some runtime anomalies are to be expected; Did you set up the classpaths correctly?", mainClass, JavaInterop.getClassloaderName(mainClassInstance.getClassLoader()), JavaInterop.getClassloaderName(cl));
            }
            Utils.startMain(mainClassInstance, args);
        } catch (Throwable t) {
            t.printStackTrace();
        }
    }
}
