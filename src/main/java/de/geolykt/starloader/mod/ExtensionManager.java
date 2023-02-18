package de.geolykt.starloader.mod;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipInputStream;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.Gson;

import net.minestom.server.extras.selfmodification.MinestomExtensionClassLoader;
import net.minestom.server.extras.selfmodification.MinestomRootClassLoader;

import de.geolykt.micromixin.MixinConfig;
import de.geolykt.starloader.launcher.ASMMixinTransformer;
import de.geolykt.starloader.mod.Extension.ExtensionDescription;
import de.geolykt.starloader.transformers.ASMTransformer;

public class ExtensionManager {

    public static final Logger LOGGER = LoggerFactory.getLogger(ExtensionManager.class);

    private static final Gson GSON = new Gson();

    private final Map<String, MinestomExtensionClassLoader> extensionLoaders = new ConcurrentHashMap<>();
    private final Map<String, Extension> extensions = new ConcurrentHashMap<>();
    private boolean loaded;

    @NotNull
    private final List<Extension> extensionList = new CopyOnWriteArrayList<>();

    @SuppressWarnings("null")
    @NotNull
    private final List<Extension> immutableExtensionListView = Collections.unmodifiableList(extensionList);

    ExtensionDescription currentlyLoadedExt;

    // Option
    private boolean loadOnStartup = true;

    public ExtensionManager() {
    }

    /**
     * Gets if the extensions should be loaded during startup.
     *
     * <p>Default value is 'true'.
     *
     */
    public boolean shouldLoadOnStartup() {
        return loadOnStartup;
    }

    /**
     * Used to specify if you want extensions to be loaded and initialised during startup.
     *
     * <p>Only useful before the server start.
     *
     * @param loadOnStartup true to load extensions on startup, false to do nothing
     */
    public void setLoadOnStartup(boolean loadOnStartup) {
        this.loadOnStartup = loadOnStartup;
    }

    public void loadExtensions(ExtensionPrototypeList extensionCandidates) {
        if (loaded) {
            throw new IllegalStateException("Extensions are already loaded!");
        }
        this.loaded = true;

        List<DiscoveredExtension> discoveredExtensions = discoverExtensions(extensionCandidates);
        discoveredExtensions = generateLoadOrder(discoveredExtensions);
        loadDependencies(discoveredExtensions);
        // remove invalid extensions
        assert discoveredExtensions != null;
        discoveredExtensions.removeIf(ext -> ext.loadStatus != DiscoveredExtension.LoadStatus.LOAD_SUCCESS);

        for (DiscoveredExtension discoveredExtension : discoveredExtensions) {
            if (discoveredExtension == null) {
                continue; // Error safety is better than having to deal with error recovered
            }
            try {
                setupClassLoader(discoveredExtension);
            } catch (Exception e) {
                discoveredExtension.loadStatus = DiscoveredExtension.LoadStatus.FAILED_TO_SETUP_CLASSLOADER;
                e.printStackTrace();
                LOGGER.error("Failed to load extension {}", discoveredExtension.getName());
                LOGGER.error("Failed to load extension", e);
            }
        }

        // remove invalid extensions
        discoveredExtensions.removeIf(ext -> ext.loadStatus != DiscoveredExtension.LoadStatus.LOAD_SUCCESS);
        setupAccessWideners(discoveredExtensions);
        setupCodeModifiers(discoveredExtensions);

        for (DiscoveredExtension discoveredExtension : discoveredExtensions) {
            if (discoveredExtension == null) {
                continue;
            }
            try {
                attemptSingleLoad(discoveredExtension);
            } catch (Exception e) {
                discoveredExtension.loadStatus = DiscoveredExtension.LoadStatus.LOAD_FAILED;
                e.printStackTrace();
                LOGGER.error("Failed to load extension {}", discoveredExtension.getName());
                LOGGER.error("Failed to load extension", e);
            }
        }
    }

    @SuppressWarnings("resource")
    private void setupClassLoader(@NotNull DiscoveredExtension discoveredExtension) {
        final String extensionName = discoveredExtension.getName();

        final URL[] urls = discoveredExtension.files.toArray(new URL[0]);
        @SuppressWarnings("null")
        final MinestomExtensionClassLoader loader = newClassLoader(discoveredExtension, urls);

        extensionLoaders.put(extensionName.toLowerCase(), loader);
    }

    @SuppressWarnings("resource")
    @Nullable
    private Extension attemptSingleLoad(@NotNull DiscoveredExtension discoveredExtension) {
        // Create ExtensionDescription (authors, version etc.)
        final String extensionName = discoveredExtension.getName();
        String mainClass = discoveredExtension.getEntrypoint();
        Extension.ExtensionDescription extensionDescription = new Extension.ExtensionDescription(
                extensionName,
                discoveredExtension.getVersion(),
                Arrays.asList(discoveredExtension.getAuthors()),
                discoveredExtension
        );

        MinestomExtensionClassLoader loader = extensionLoaders.get(extensionName.toLowerCase());

        if (extensions.containsKey(extensionName.toLowerCase())) {
            LOGGER.error("An extension called '{}' has already been registered.", extensionName);
            return null;
        }

        Class<?> jarClass;
        try {
            jarClass = Class.forName(mainClass, true, loader);
        } catch (ClassNotFoundException e) {
            LOGGER.error("Could not find main class '{}' in extension '{}'.", mainClass, extensionName, e);
            return null;
        }

        Class<? extends Extension> extensionClass;
        try {
            extensionClass = jarClass.asSubclass(Extension.class);
        } catch (ClassCastException e) {
            LOGGER.error("Main class '{}' in '{}' does not extend the 'Extension' superclass.", mainClass, extensionName, e);
            return null;
        }

        Constructor<? extends Extension> constructor;
        try {
            constructor = extensionClass.getDeclaredConstructor();
            // Let's just make it accessible, plugin creators don't have to make this public.
            constructor.setAccessible(true);
        } catch (NoSuchMethodException e) {
            LOGGER.error("Main class '{}' in '{}' does not define a no-args constructor.", mainClass, extensionName, e);
            return null;
        }
        Extension extension = null;
        try {
            currentlyLoadedExt = extensionDescription;
            extension = constructor.newInstance();
        } catch (InstantiationException e) {
            LOGGER.error("Main class '{}' in '{}' cannot be an abstract class.", mainClass, extensionName, e);
            return null;
        } catch (IllegalAccessException ignored) {
            // We made it accessible, should not occur
        } catch (InvocationTargetException e) {
            LOGGER.error("While instantiating the main class '{}' in '{}' an exception was thrown.", mainClass, extensionName, e.getTargetException()
            );
            return null;
        } finally {
            currentlyLoadedExt = null;
        }

        // add dependents to pre-existing extensions, so that they can easily be found during reloading
        for (String dependency : discoveredExtension.getDependencies()) {
            Extension dep = extensions.get(dependency.toLowerCase());
            if (dep == null) {
                LOGGER.warn("Dependency {} of {} is null? This means the extension has been loaded without its dependency, which could cause issues later.", dependency, discoveredExtension.getName());
            } else {
                dep.getDescription().getDependents().add(discoveredExtension.getName());
            }
        }

        extensionList.add(extension); // add to a list, as lists preserve order
        extensions.put(extensionName.toLowerCase(), extension);

        return extension;
    }

    @NotNull
    private List<DiscoveredExtension> discoverExtensions(ExtensionPrototypeList extensionCandidates) {
        List<DiscoveredExtension> extensions = new LinkedList<>();
        for (ExtensionPrototype prototype : extensionCandidates.getPrototypes()) {
            if (prototype.enabled) {
                File file = prototype.origin;
                DiscoveredExtension extension = discoverFromJar(file);
                if (extension != null && extension.loadStatus == DiscoveredExtension.LoadStatus.LOAD_SUCCESS) {
                    extensions.add(extension);
                }
            }
        }
        return extensions;
    }

    private DiscoveredExtension discoverFromJar(File file) {
        try (ZipFile f = new ZipFile(file)) {
            try (InputStreamReader reader = new InputStreamReader(f.getInputStream(f.getEntry("extension.json")))) {
                @SuppressWarnings("null")
                DiscoveredExtension extension = GSON.fromJson(reader, DiscoveredExtension.class);
                extension.setOriginalJar(file);
                extension.files.add(file.toURI().toURL());

                // Verify integrity and ensure defaults
                DiscoveredExtension.verifyIntegrity(extension);

                return extension;
            }
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
    }

    @Nullable
    private List<DiscoveredExtension> generateLoadOrder(@NotNull List<DiscoveredExtension> discoveredExtensions) {
        // Do some mapping so we can map strings to extensions.
        Map<String, DiscoveredExtension> extensionMap = new HashMap<>();
        Map<DiscoveredExtension, List<DiscoveredExtension>> dependencyMap = new HashMap<>();
        for (DiscoveredExtension discoveredExtension : discoveredExtensions) {
            extensionMap.put(discoveredExtension.getName().toLowerCase(), discoveredExtension);
        }
        for (DiscoveredExtension discoveredExtension : discoveredExtensions) {

            @SuppressWarnings("null")
            List<DiscoveredExtension> dependencies = Arrays.stream(discoveredExtension.getDependencies())
                    .map(dependencyName -> {
                        DiscoveredExtension dependencyExtension = extensionMap.get(dependencyName.toLowerCase());
                        // Specifies an extension we don't have.
                        if (dependencyExtension == null) {
                            // attempt to see if it is not already loaded (happens with dynamic (re)loading)
                            if (extensions.containsKey(dependencyName.toLowerCase())) {
                                return extensions.get(dependencyName.toLowerCase()).getDescription().getOrigin();
                            }
                            LOGGER.error("Extension {} requires an extension called {}.", discoveredExtension.getName(), dependencyName);
                            LOGGER.error("However the extension {} could not be found.", dependencyName);
                            LOGGER.error("Therefore {} will not be loaded.", discoveredExtension.getName());
                            discoveredExtension.loadStatus = DiscoveredExtension.LoadStatus.MISSING_DEPENDENCIES;
                        }
                        // This will return null for an unknown-extension
                        return extensionMap.get(dependencyName.toLowerCase());
                    }).collect(Collectors.toList());

            // If the list contains null ignore it.
            if (!dependencies.contains(null)) {
                dependencyMap.put(
                        discoveredExtension,
                        dependencies
                );
            }
        }

        // List containing the real load order.
        LinkedList<DiscoveredExtension> sortedList = new LinkedList<>();

        // entries with empty lists
        List<Map.Entry<DiscoveredExtension, List<DiscoveredExtension>>> loadableExtensions;
        // While there are entries with no more elements (no more dependencies)
        while (!(loadableExtensions = dependencyMap.entrySet().stream().filter(entry -> areAllDependenciesLoaded(entry.getValue())).collect(Collectors.toList())).isEmpty()) {
            // Get all "loadable" (not actually being loaded!) extensions and put them in the sorted list.
            for (Map.Entry<DiscoveredExtension, List<DiscoveredExtension>> entry : loadableExtensions) {
                // Add to sorted list.
                sortedList.add(entry.getKey());
                // Remove to make the next iterations a little bit quicker (hopefully) and to find cyclic dependencies.
                dependencyMap.remove(entry.getKey());
                // Remove this dependency from all the lists (if they include it) to make way for next level of extensions.
                dependencyMap.forEach((key, dependencyList) -> dependencyList.remove(entry.getKey()));
            }
        }

        // Check if there are cyclic extensions.
        if (!dependencyMap.isEmpty()) {
            LOGGER.error("Minestom found {} cyclic extensions.", dependencyMap.size());
            LOGGER.error("Cyclic extensions depend on each other and can therefore not be loaded.");
            for (Map.Entry<DiscoveredExtension, List<DiscoveredExtension>> entry : dependencyMap.entrySet()) {
                DiscoveredExtension discoveredExtension = entry.getKey();
                LOGGER.error("{} could not be loaded, as it depends on: {}.",
                        discoveredExtension.getName(),
                        entry.getValue().stream().map(DiscoveredExtension::getName).collect(Collectors.joining(", ")));
            }
        }

        return sortedList;
    }

    private boolean areAllDependenciesLoaded(List<DiscoveredExtension> dependencies) {
        return dependencies.isEmpty() || dependencies.stream().allMatch(ext -> extensions.containsKey(ext.getName().toLowerCase()));
    }

    private void loadDependencies(List<DiscoveredExtension> extensions) {
        List<DiscoveredExtension> allLoadedExtensions = new LinkedList<>(extensions);
        extensionList.stream().map(ext -> ext.getDescription().getOrigin()).forEach(allLoadedExtensions::add);
    }

    /**
     * Creates a new class loader for the given extension.
     * Will add the new loader as a child of all its dependencies' loaders.
     *
     * @param urls {@link URL} (usually a JAR) that should be loaded.
     */
    @SuppressWarnings("resource")
    @NotNull
    public MinestomExtensionClassLoader newClassLoader(@NotNull DiscoveredExtension extension, @NotNull URL[] urls) {
        MinestomRootClassLoader root = MinestomRootClassLoader.getInstance();
        MinestomExtensionClassLoader loader = new MinestomExtensionClassLoader(extension.getName(), urls, root);
        if (extension.getDependencies().length == 0) {
            // orphaned extension, we can insert it directly
            root.addChild(loader);
        } else {
            // we need to keep track that it has actually been inserted
            // even though it should always be (due to the order in which extensions are loaders), it is an additional layer of """security"""
            boolean foundOne = false;
            for (String dependency : extension.getDependencies()) {
                if (extensionLoaders.containsKey(dependency.toLowerCase())) {
                    MinestomExtensionClassLoader parentLoader = extensionLoaders.get(dependency.toLowerCase());
                    parentLoader.addChild(loader);
                    foundOne = true;
                }
            }

            if (!foundOne) {
                LOGGER.error("Could not load extension {}, could not find any parent inside classloader hierarchy.", extension.getName());
                throw new RuntimeException("Could not load extension " + extension.getName() + ", could not find any parent inside classloader hierarchy.");
            }
        }
        return loader;
    }

    @NotNull
    public List<Extension> getExtensions() {
        return immutableExtensionListView;
    }

    @Nullable
    public Extension getExtension(@NotNull String name) {
        return extensions.get(name.toLowerCase());
    }

    @NotNull
    public Map<String, URLClassLoader> getExtensionLoaders() {
        return new HashMap<>(extensionLoaders);
    }

    @SuppressWarnings("resource")
    private void setupAccessWideners(List<DiscoveredExtension> extensionsToLoad) {
        for (DiscoveredExtension extension : extensionsToLoad) {
            if (extension.getAccessWidener().equals("")) {
                continue;
            }

            try {
                JarFile jar = new JarFile(extension.getOriginalJar(), false);
                JarEntry entry = jar.getJarEntry(extension.getAccessWidener());
                if (entry == null) {
                    LOGGER.warn("Unable to find the access widener file for extension {}!", extension.getName());
                    jar.close();
                    continue;
                }
                try (InputStream awFile = jar.getInputStream(entry)) {
                    if (awFile == null) {
                        throw new NullPointerException("jar.getInputStream(entry) yielded null");
                    }
                    MinestomRootClassLoader.getInstance().readAccessWidener(awFile);
                } catch (IOException e) {
                    e.printStackTrace();
                    LOGGER.warn("Failed to set up an access widener for {}!", extension.getName());
                } finally {
                    jar.close();
                }
            } catch (IOException e) {
                e.printStackTrace();
                LOGGER.warn("Failed to set up an access widener for {}!", extension.getName());
            }
        }
    }

    /**
     * Extensions are allowed to apply Mixin transformers, the magic happens here.
     */
    private void setupCodeModifiers(@NotNull List<DiscoveredExtension> extensions) {
        // FIXME code modifiers persist (particularly asm transformers and mixins) even though the extension never started correctly
        ClassLoader cl = getClass().getClassLoader();
        if (!(cl instanceof MinestomRootClassLoader)) {
            // The extension manager class is excluded from the root classloader, so yeah...
            cl = MinestomRootClassLoader.getInstance();
        }
        @SuppressWarnings("resource")
        MinestomRootClassLoader modifiableClassLoader = (MinestomRootClassLoader) cl;
        LOGGER.info("Start loading code modifiers...");
        for (DiscoveredExtension extension : extensions) {
            try {
                for (String codeModifierClass : extension.getCodeModifiers()) {
                    modifiableClassLoader.loadModifier(extension.files.toArray(new URL[0]), codeModifierClass);
                }
                if (!extension.getMixinConfig().isEmpty()) {
                    final String mixinConfigFile = extension.getMixinConfig();
                    boolean added = false;
                    for (ASMTransformer transformer : modifiableClassLoader.getTransformers()) {
                        if (transformer instanceof ASMMixinTransformer) {
                            JSONObject mixinConfigJson = null;
                            for (URL url : extension.files) {
                                try (ZipInputStream zipIn = new ZipInputStream(url.openStream())) {
                                    for (ZipEntry entry = zipIn.getNextEntry(); entry != null; entry = zipIn.getNextEntry()) {
                                        if (entry.getName().equals(mixinConfigFile)) {
                                            mixinConfigJson = new JSONObject(new String(zipIn.readAllBytes(), StandardCharsets.UTF_8));
                                        }
                                    }
                                } catch (IOException e) {
                                    LOGGER.warn("Unable to open jar \"" + url + "\".", e);
                                }
                            }
                            if (mixinConfigJson == null) {
                                throw new IOException("Cannot find mixin config " + mixinConfigFile + " in extension " + extension.getName());
                            }
                            MixinConfig mixinConfig = MixinConfig.fromJson(mixinConfigJson);
                            // TODO Use Classloader futures in order to sandbox different mixins
                            // This reduces the chance of collision of two mixins
                            ((ASMMixinTransformer) transformer).transformer.addMixin(modifiableClassLoader, mixinConfig);
                            added = true;
                            LOGGER.info("Found mixin in extension {}: {}", extension.getName(), mixinConfigFile);
                        }
                    }
                    if (!added) {
                        LOGGER.error("Unable to add mixin {} in extension {} as there is no classloader!");
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
                LOGGER.error("Failed to load code modifier for extension in files: "
                        + extension.files
                                .stream()
                                .map(URL::toExternalForm)
                                .collect(Collectors.joining(", ")), e);
            }
        }
        LOGGER.info("Done loading code modifiers.");
    }

    @SuppressWarnings({ "resource", "deprecation" })
    private void unload(Extension ext) {
        ext.preTerminate();
        ext.terminate();
        ext.postTerminate();
        ext.unload();

        // remove as dependent of other extensions
        // this avoids issues where a dependent extension fails to reload, and prevents the base extension to reload too
        for (Extension e : extensionList) {
            e.getDescription().getDependents().remove(ext.getDescription().getName());
        }

        String id = ext.getDescription().getName().toLowerCase();
        // remove from loaded extensions
        extensions.remove(id);
        extensionList.remove(ext);

        // remove class loader, required to reload the classes
        MinestomExtensionClassLoader classloader = extensionLoaders.remove(id);
        try {
            // close resources
            classloader.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
        MinestomRootClassLoader.getInstance().removeChildInHierarchy(classloader);
    }

    public void reload(String extensionName) {
        Extension ext = extensions.get(extensionName.toLowerCase());
        if (ext == null) {
            throw new IllegalArgumentException("Extension " + extensionName + " is not currently loaded.");
        }

        File originalJar = ext.getDescription().getOrigin().getOriginalJar();
        if (originalJar == null) {
            LOGGER.error("Cannot reload extension {} that is not from a .jar file!", extensionName);
            return;
        }

        LOGGER.info("Reload extension {} from jar file {}", extensionName, originalJar.getAbsolutePath());
        List<String> dependents = new LinkedList<>(ext.getDescription().getDependents()); // copy dependents list
        List<File> originalJarsOfDependents = new LinkedList<>();

        for (String dependentID : dependents) {
            Extension dependentExt = extensions.get(dependentID.toLowerCase());
            File dependentOriginalJar = dependentExt.getDescription().getOrigin().getOriginalJar();
            originalJarsOfDependents.add(dependentOriginalJar);
            if (dependentOriginalJar == null) {
                LOGGER.error("Cannot reload extension {} that is not from a .jar file!", dependentID);
                return;
            }

            LOGGER.info("Unloading dependent extension {} (because it depends on {})", dependentID, extensionName);
            unload(dependentExt);
        }

        LOGGER.info("Unloading extension {}", extensionName);
        unload(ext);

        System.gc();

        // ext and its dependents should no longer be referenced from now on

        // rediscover extension to reload. We allow dependency changes, so we need to fully reload it
        List<DiscoveredExtension> extensionsToReload = new LinkedList<>();
        LOGGER.info("Rediscover extension {} from jar {}", extensionName, originalJar.getAbsolutePath());
        DiscoveredExtension rediscoveredExtension = discoverFromJar(originalJar);
        extensionsToReload.add(rediscoveredExtension);

        for (File dependentJar : originalJarsOfDependents) {
            // rediscover dependent extension to reload
            LOGGER.info("Rediscover dependent extension (depends on {}) from jar {}", extensionName, dependentJar.getAbsolutePath());
            extensionsToReload.add(discoverFromJar(dependentJar));
        }

        // ensure correct order of dependencies
        loadExtensionList(extensionsToReload);
    }

    public boolean loadDynamicExtension(File jarFile) throws FileNotFoundException {
        if (!jarFile.exists()) {
            throw new FileNotFoundException("File '" + jarFile.getAbsolutePath() + "' does not exists. Cannot load extension.");
        }

        LOGGER.info("Discover dynamic extension from jar {}", jarFile.getAbsolutePath());
        DiscoveredExtension discoveredExtension = discoverFromJar(jarFile);
        List<DiscoveredExtension> extensionsToLoad = Collections.singletonList(discoveredExtension);
        if (extensionsToLoad == null) {
            throw new InternalError();
        }
        return loadExtensionList(extensionsToLoad);
    }

    private boolean loadExtensionList(@NotNull List<DiscoveredExtension> extensionsToLoad) {
        // ensure correct order of dependencies
        LOGGER.debug("Reorder extensions to ensure proper load order");
        var temp = generateLoadOrder(extensionsToLoad);
        if (temp == null) {
            throw new AssertionError();
        }
        loadDependencies(extensionsToLoad);

        // setup new classloaders for the extensions to reload
        for (DiscoveredExtension toReload : extensionsToLoad) {
            LOGGER.debug("Setting up classloader for extension {}", toReload.getName());
            setupClassLoader(toReload);
        }

        setupAccessWideners(extensionsToLoad);
        // setup code modifiers for these extensions
        // TODO: it is possible that the new modifiers cannot be applied (because the targeted classes are already loaded), should we issue a warning?
        // If so, how?
        setupCodeModifiers(extensionsToLoad);

        List<Extension> newExtensions = new LinkedList<>();
        for (DiscoveredExtension toReload : extensionsToLoad) {
            // reload extensions
            LOGGER.info("Actually load extension {}", toReload.getName());
            Extension loadedExtension = attemptSingleLoad(toReload);
            if (loadedExtension != null) {
                newExtensions.add(loadedExtension);
            }
        }

        if (newExtensions.isEmpty()) {
            LOGGER.error("No extensions to load, skipping callbacks");
            return false;
        }

        LOGGER.info("Load complete, firing preinit, init and then postinit callbacks");
        // retrigger preinit, init and postinit
        newExtensions.forEach(Extension::preInitialize);
        newExtensions.forEach(Extension::initialize);
        newExtensions.forEach(Extension::postInitialize);
        return true;
    }

    public void unloadExtension(String extensionName) {
        Extension ext = extensions.get(extensionName.toLowerCase());
        if (ext == null) {
            throw new IllegalArgumentException("Extension " + extensionName + " is not currently loaded.");
        }
        List<String> dependents = new LinkedList<>(ext.getDescription().getDependents()); // copy dependents list

        for (String dependentID : dependents) {
            Extension dependentExt = extensions.get(dependentID.toLowerCase());
            LOGGER.info("Unloading dependent extension {} (because it depends on {})", dependentID, extensionName);
            unload(dependentExt);
        }

        LOGGER.info("Unloading extension {}", extensionName);
        unload(ext);

        // call GC to try to get rid of classes and classloader
        System.gc();
    }

    /**
     * Shutdowns all the extensions by unloading them.
     */
    public void shutdown() {
        this.extensionList.forEach(this::unload);
    }

    void getDescription(Extension extension) {
        //
    }
}
