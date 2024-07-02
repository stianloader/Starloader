package de.geolykt.starloader.mod;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.stream.Collectors;

import org.jetbrains.annotations.ApiStatus.AvailableSince;
import org.jetbrains.annotations.ApiStatus.Internal;
import org.jetbrains.annotations.ApiStatus.ScheduledForRemoval;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.stianloader.micromixin.transform.api.MixinConfig;
import org.stianloader.picoresolve.DependencyLayer;
import org.stianloader.picoresolve.DependencyLayer.DependencyEdge;
import org.stianloader.picoresolve.DependencyLayer.DependencyLayerElement;
import org.stianloader.picoresolve.GAV;
import org.stianloader.picoresolve.MavenResolver;
import org.stianloader.picoresolve.Scope;
import org.stianloader.picoresolve.exclusion.ExclusionContainer;
import org.stianloader.picoresolve.repo.MavenRepository;
import org.stianloader.picoresolve.repo.RepositoryAttachedValue;
import org.stianloader.picoresolve.version.MavenVersion;

import net.minestom.server.extras.selfmodification.MinestomExtensionClassLoader;
import net.minestom.server.extras.selfmodification.MinestomRootClassLoader;

import de.geolykt.starloader.launcher.ASMMixinTransformer;
import de.geolykt.starloader.mod.DiscoveredExtension.ExternalDependencies;
import de.geolykt.starloader.mod.DiscoveredExtension.ExternalDependencyArtifact;
import de.geolykt.starloader.mod.DiscoveredExtension.ExternalRepository;
import de.geolykt.starloader.mod.DiscoveredExtension.LoadStatus;
import de.geolykt.starloader.mod.Extension.ExtensionDescription;
import de.geolykt.starloader.transformers.ASMTransformer;
import de.geolykt.starloader.transformers.ReversibleAccessSetterTransformer;
import de.geolykt.starloader.util.JavaInterop;
import de.geolykt.starloader.util.MirroringURIMavenRepository;

public class ExtensionManager {

    /**
     * The description of the extension that is currently being loaded.
     * This is internally used to set the description of an extension before the constructor runs
     * without having to rely on massive hacks using the sun Unsafe.
     */
    static final ThreadLocal<ExtensionDescription> CURRENTLY_LOADED_EXTENSION = new ThreadLocal<>();

    @Internal
    public static final Logger LOGGER = LoggerFactory.getLogger(ExtensionManager.class);

    /**
     * Whether to dump ALL data retrieved using {@link MirroringURIMavenRepository} as-is.
     *
     * @since 4.0.0-a20240601
     */
    @AvailableSince("4.0.0-a20240601")
    private static final boolean MIRROR_MAVEN_REQUESTS = Boolean.getBoolean("org.stianloader.sll.log.MIRROR_MAVEN_REQUESTS");

    private final Map<String, MinestomExtensionClassLoader> extensionClassloaders = new HashMap<>();
    @NotNull
    private final List<Extension> extensionList = new CopyOnWriteArrayList<>();
    private final Map<String, Extension> extensions = new ConcurrentHashMap<>();
    @NotNull
    private final List<Extension> immutableExtensionListView = Collections.unmodifiableList(this.extensionList);
    private boolean loaded;
    @NotNull
    @AvailableSince("4.0.0-a20240601")
    private final Path mavenCacheDir;

    @Deprecated
    @ScheduledForRemoval(inVersion = "5.0.0")
    public ExtensionManager() {
        this(Paths.get(".picoresolve-cache"));
    }

    public ExtensionManager(@NotNull Path mavenCacheDir) {
        this.mavenCacheDir = mavenCacheDir;
    }

    public void loadExtensions(List<@NotNull ? extends ExtensionPrototype> extensionCandidates) {
        if (this.loaded) {
            throw new IllegalStateException("Extensions are already loaded!");
        }
        this.loaded = true;

        List<DiscoveredExtension> discoveredExtensions = this.discoverExtensions(extensionCandidates);
        discoveredExtensions = this.generateLoadOrder(discoveredExtensions);
        // remove invalid extensions
        assert discoveredExtensions != null;
        discoveredExtensions.removeIf(ext -> ext.getLoadStatus() != DiscoveredExtension.LoadStatus.LOAD_SUCCESS);

        for (DiscoveredExtension extension : discoveredExtensions) {
            if (extension.loader != null) {
                try {
                    extension.loader.close();
                } catch (IOException e) {
                    ExtensionManager.LOGGER.warn("Unable to close leftover classloader for extension {}", extension.getName(), e);
                }
                extension.loader = null;
            }
            if (extension.getLoadStatus() == LoadStatus.LOAD_SUCCESS) {
                extension.loader = this.newClassLoader(extension);
            }
        }

        this.setupAccessWideners(discoveredExtensions);
        this.setupCodeModifiers(discoveredExtensions);

        // remove invalid extensions
        discoveredExtensions.removeIf(ext -> ext.getLoadStatus() != DiscoveredExtension.LoadStatus.LOAD_SUCCESS);

        for (DiscoveredExtension discoveredExtension : discoveredExtensions) {
            if (discoveredExtension == null) {
                continue;
            }
            try {
                this.attemptSingleLoad(discoveredExtension);
            } catch (Exception e) {
                discoveredExtension.setLoadStatus(DiscoveredExtension.LoadStatus.LOAD_FAILED);
                e.printStackTrace();
                LOGGER.error("Failed to load extension {}", discoveredExtension.getName());
                LOGGER.error("Failed to load extension", e);
            }
        }
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

        MinestomExtensionClassLoader loader = discoveredExtension.loader;

        if (this.extensions.containsKey(extensionName.toLowerCase(Locale.ROOT))) {
            LOGGER.error("An extension called '{}' has already been registered.", extensionName);
            return null;
        }

        Class<?> jarClass;
        try {
            jarClass = loader.loadClassAsChild(mainClass.replace('/', '.'), true);
            if (jarClass.getClassLoader() != loader) {
                throw new ClassNotFoundException("Class " + jarClass.getName() + " is loaded by classloader \"" + JavaInterop.getClassloaderName(jarClass.getClassLoader()) + "\", but expected it to be loaded by \"" + JavaInterop.getClassloaderName(loader) + "\"");
            }
        } catch (ClassNotFoundException e) {
            LOGGER.error("Could not find main class '{}' in extension '{}' with associated URLs '{}'.", mainClass, extensionName, extensionDescription.getOrigin().files, e);
            return null;
        }

        Class<? extends Extension> extensionClass;
        try {
            extensionClass = jarClass.asSubclass(Extension.class);
        } catch (ClassCastException e) {
            LOGGER.error("Main class '{}' in '{}' does not extend the 'Extension' superclass. Instead it directly extends '{}' from classloader '{}'", mainClass, extensionName, jarClass.getSuperclass().getName(), JavaInterop.getClassloaderName(jarClass.getSuperclass().getClassLoader()), e);
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
            CURRENTLY_LOADED_EXTENSION.set(extensionDescription);
            extension = constructor.newInstance();
        } catch (InstantiationException e) {
            LOGGER.error("Main class '{}' in '{}' cannot be an abstract class.", mainClass, extensionName, e);
            return null;
        } catch (IllegalAccessException ignored) {
            // We made it accessible, should not occur
        } catch (InvocationTargetException e) {
            LOGGER.error("While instantiating the main class '{}' in '{}' an exception was thrown.", mainClass, extensionName, e.getTargetException());
            return null;
        } finally {
            CURRENTLY_LOADED_EXTENSION.set(null);
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

        this.extensionList.add(extension); // add to a list, as lists preserve order
        this.extensions.put(extensionName.toLowerCase(), extension);

        return extension;
    }

    @NotNull
    private List<DiscoveredExtension> discoverExtensions(List<@NotNull ? extends ExtensionPrototype> extensionCandidates) {
        List<DiscoveredExtension> extensions = new LinkedList<>();
        for (ExtensionPrototype prototype : extensionCandidates) {
            if (prototype.enabled) {
                DiscoveredExtension extension = discoverFromURLs(prototype.originURLs);
                if (extension != null && extension.getLoadStatus() == DiscoveredExtension.LoadStatus.LOAD_SUCCESS) {
                    extensions.add(extension);
                }
            }
        }
        return extensions;
    }

    @Nullable
    private DiscoveredExtension discoverFromURLs(List<URL> urls) {
        URLClassLoader discardLoader = MinestomRootClassLoader.getInstance().newChild(urls.toArray(new @NotNull URL[0]));
        try {
            URL resource = discardLoader.findResource("extension.json");
            if (resource == null) {
                throw new IOException("Extension does not have an extension.json file: " + urls);
            }
            try (InputStream is = resource.openStream()) {
                DiscoveredExtension extension = DiscoveredExtension.fromJSON(is);
                extension.files.addAll(urls);

                // Verify integrity and ensure defaults
                DiscoveredExtension.verifyIntegrity(extension);

                discardLoader.close();
                return extension;
            }
        } catch (IOException e) {
            try {
                discardLoader.close();
            } catch (IOException e2) {
                UncheckedIOException unchecked = new UncheckedIOException("Cannot close temporary discard classloader", e2);
                unchecked.addSuppressed(e);
                throw unchecked;
            }
            e.printStackTrace();
            return null;
        } catch (Throwable t) {
            try {
                discardLoader.close();
            } catch (IOException e) {
                UncheckedIOException unchecked = new UncheckedIOException("Cannot close temporary discard classloader", e);
                unchecked.addSuppressed(t);
                throw unchecked;
            }
            throw t;
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
                            if (this.extensions.containsKey(dependencyName.toLowerCase(Locale.ROOT))) {
                                return this.extensions.get(dependencyName.toLowerCase(Locale.ROOT)).getDescription().getOrigin();
                            }
                            LOGGER.error("Extension {} requires an extension called {}.", discoveredExtension.getName(), dependencyName);
                            LOGGER.error("However the extension {} could not be found.", dependencyName);
                            LOGGER.error("Therefore {} will not be loaded.", discoveredExtension.getName());
                            discoveredExtension.setLoadStatus(DiscoveredExtension.LoadStatus.MISSING_DEPENDENCIES);
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
        return dependencies.isEmpty() || dependencies.stream().allMatch(ext -> this.extensions.containsKey(ext.getName().toLowerCase()));
    }

    /**
     * Creates a new class loader for the given extension.
     * Will add the new loader as a child of all its dependencies' loaders.
     *
     * @param extension The extension that should own the classloader
     * @since 4.0.0-20230701
     */
    @SuppressWarnings("resource")
    @NotNull
    public MinestomExtensionClassLoader newClassLoader(@NotNull DiscoveredExtension extension) {
        MinestomRootClassLoader root = MinestomRootClassLoader.getInstance();
        MinestomExtensionClassLoader loader = new MinestomExtensionClassLoader(extension.getName(), extension.files.toArray(new URL[0]), root);

        ExternalDependencies dependencies = extension.getExternalDependencies();
        resolveDependencies:
        if (dependencies != null) {
            // Resolve maven dependencies

            List<DependencyEdge> dependencyEdges = new ArrayList<>();

            for (ExternalDependencyArtifact artifact : dependencies.getArtifacts()) {
                dependencyEdges.add(new DependencyEdge(artifact.getGroup(), artifact.getArtifact(), artifact.getClassifier(), artifact.getExtension(), artifact.getVersion(), Scope.RUNTIME, artifact.getExclusions()));
            }

            if (dependencyEdges.isEmpty()) {
                break resolveDependencies;
            }

            MavenResolver resolver = new MavenResolver(this.mavenCacheDir);
            for (ExternalRepository repo : dependencies.getRepositories()) {
                Path mirrorOut = (repo.isMirrorable() && ExtensionManager.MIRROR_MAVEN_REQUESTS) ? this.mavenCacheDir.resolve(".mirror-out") : null;
                if (!repo.isMirrorOnly() || ExtensionManager.MIRROR_MAVEN_REQUESTS) {
                    resolver.addRepository(new MirroringURIMavenRepository(repo.getName(), URI.create(repo.getUrl()), mirrorOut));
                }
            }

            GAV virtualGAV = new GAV("sll-virtual-dependency", extension.getName(), MavenVersion.parse(extension.getVersion()));
            DependencyLayerElement virtualElement = new DependencyLayerElement(virtualGAV, null, null, ExclusionContainer.empty(), dependencyEdges);
            DependencyLayer layer = new DependencyLayer(null, Collections.singletonList(virtualElement));

            Executor executor = Objects.requireNonNull(ForkJoinPool.commonPool());

            try {
                resolver.resolveAllChildren(layer, executor).get(60, TimeUnit.SECONDS);
            } catch (CompletionException | ExecutionException e) {
                ExtensionManager.LOGGER.error("Unable to fetch remote dependencies of extension {} v{}.", extension.getName(), extension.getVersion());
                throw new RuntimeException("Unable to fetch remote dependencies of extension " + extension.getName() + ", version " + extension.getVersion(), e);
            } catch (TimeoutException | InterruptedException e) {
                ExtensionManager.LOGGER.error("Unable to fetch remote dependencies of extension {} v{} (timed out. Extensions have a maximum of 60 seconds to resolve remove dependencies).", extension.getName(), extension.getVersion());
                throw new RuntimeException("Unable to fetch remote dependencies of extension " + extension.getName() + ", version " + extension.getVersion() + ": Timed out", e);
            }

            CompletableFuture<Set<RepositoryAttachedValue<Path>>> combined = CompletableFuture.completedFuture(ConcurrentHashMap.newKeySet());

            List<GAV> dependencyGAVs = new ArrayList<>();
            for (layer = layer.getChild(); layer != null; layer = layer.getChild()) {
                for (DependencyLayerElement element : layer.elements) {
                    dependencyGAVs.add(element.gav);
                    CompletableFuture<RepositoryAttachedValue<Path>> download = resolver.download(element.gav, element.classifier, element.type, executor);
                    combined = combined.thenCombine(download, (set, downloadResult) -> {
                        set.add(downloadResult);
                        return set;
                    });
                }
            }

            Set<RepositoryAttachedValue<Path>> paths;

            try {
                paths = combined.get(60, TimeUnit.SECONDS);
            } catch (CompletionException | ExecutionException e) {
                ExtensionManager.LOGGER.error("Unable to download remote dependencies of extension {} v{}. Requested GAVs: {}", extension.getName(), extension.getVersion(), dependencyGAVs);
                throw new RuntimeException("Unable to download remote dependencies of extension " + extension.getName() + ", version " + extension.getVersion(), e);
            } catch (TimeoutException | InterruptedException e) {
                ExtensionManager.LOGGER.error("Unable to download remote dependencies of extension {} v{} (timed out. Extensions have a maximum of 60 seconds to resolve remove dependencies). Requested GAVs: {}", extension.getName(), extension.getVersion(), dependencyGAVs);
                throw new RuntimeException("Unable to download remote dependencies of extension " + extension.getName() + ", version " + extension.getVersion() + ": Timed out", e);
            }

            for (RepositoryAttachedValue<Path> value : paths) {
                String repoId = "null (cached in maven local, but without a known maven repository)";
                MavenRepository repo = value.getRepository();
                if (repo != null) {
                    repoId = repo.getRepositoryId();
                }
                ExtensionManager.LOGGER.debug("Adding {} to the classpath of extension '{}' v{}. The dependency was found in repository '{}'", value.getValue(), extension.getName(), extension.getVersion(), repoId);
                try {
                    loader.addURL(value.getValue().toUri().toURL());
                } catch (MalformedURLException e) {
                    ExtensionManager.LOGGER.warn("Failed to add {} to the classpath of extension '{}' v{}. The dependency was found in repository '{}'", value.getValue(), extension.getName(), extension.getVersion(), repoId, e);
                }
            }
        }

        if (extension.getDependencies().length == 0) {
            // orphaned extension, we can insert it directly
            root.addChild(loader);
        } else {
            // we need to keep track that it has actually been inserted
            // even though it should always be (due to the order in which extensions are loaders), it is an additional layer of """security"""
            boolean missedOne = false;
            for (String dependency : extension.getDependencies()) {
                MinestomExtensionClassLoader parent = this.extensionClassloaders.get(dependency.toLowerCase(Locale.ROOT));
                if (parent != null) {
                    parent.addChild(loader);
                } else {
                    missedOne = true;
                }
            }

            if (missedOne) {
                ExtensionManager.LOGGER.error("Could not load extension '{}' as it was not possible to find any of the following parents in the classloader hierarchy: {}. Following classloaders are currently registered: {}", extension.getName(), Arrays.toString(extension.getDependencies()), this.extensionClassloaders.keySet());
                throw new RuntimeException("Could not load extension " + extension.getName() + " as it was not possible find any of the following parents inside classloader hierarchy (this indicates a likely issue with SLL internals): " + Arrays.toString(extension.getDependencies()));
            }
        }

        this.extensionClassloaders.put(extension.getName().toLowerCase(Locale.ROOT), loader);
        return loader;
    }

    @NotNull
    public List<Extension> getExtensions() {
        return this.immutableExtensionListView;
    }

    @Nullable
    public Extension getExtension(@NotNull String name) {
        return this.extensions.get(name.toLowerCase());
    }

    @SuppressWarnings("deprecation")
    private void setupAccessWideners(List<DiscoveredExtension> extensionsToLoad) {
        for (DiscoveredExtension extension : extensionsToLoad) {
            if (extension.getLoadStatus() != LoadStatus.LOAD_SUCCESS || extension.getAccessWidener().equals("")) {
                continue;
            }

            URL entry = extension.loader.findResource(extension.getAccessWidener());
            if (entry == null) {
                LOGGER.warn("Unable to find the access widener file for extension {}!", extension.getName());
                continue;
            }
            try (InputStream awFile = entry.openStream()) {
                if (awFile == null) {
                    throw new NullPointerException("entry.openStream() yielded null");
                }
                MinestomRootClassLoader.getInstance().readAccessWidener(awFile);
            } catch (IOException e) {
                e.printStackTrace();
                LOGGER.warn("Failed to set up an access widener for {}!", extension.getName());
            }
        }

        ReversibleAccessSetterTransformer transformer = null;
        for (ASMTransformer asmTransformer : MinestomRootClassLoader.getInstance().getTransformers()) {
            if (asmTransformer instanceof ReversibleAccessSetterTransformer) {
                transformer = (ReversibleAccessSetterTransformer) asmTransformer;
                break;
            }
        }
        if (transformer == null) {
            transformer = new ReversibleAccessSetterTransformer();
            MinestomRootClassLoader.getInstance().addTransformer(transformer);
        }

        for (DiscoveredExtension extension : extensionsToLoad) {
            if (extension.getLoadStatus() != LoadStatus.LOAD_SUCCESS || extension.getReversibleAccessSetter().isEmpty()) {
                continue;
            }

            URL entry = extension.loader.findResource(extension.getReversibleAccessSetter());
            if (entry == null) {
                LOGGER.warn("Unable to find the reversible access setter file for extension {}!", extension.getName());
                continue;
            }
            try (InputStream rasFile = entry.openStream()) {
                if (rasFile == null) {
                    throw new NullPointerException("entry.openStream() yielded null");
                }
                try (InputStreamReader isr = new InputStreamReader(rasFile, StandardCharsets.UTF_8);
                        BufferedReader br = new BufferedReader(isr)) {
                    transformer.getReverseContext().read(extension.getName(), br, true);
                }
            } catch (IOException e) {
                e.printStackTrace();
                LOGGER.warn("Failed to set up the reversed reversible access setter for {}!", extension.getName());
                continue;
            }
            try (InputStream rasFile = entry.openStream()) {
                if (rasFile == null) {
                    throw new NullPointerException("entry.openStream() yielded null");
                }
                try (InputStreamReader isr = new InputStreamReader(rasFile, StandardCharsets.UTF_8);
                        BufferedReader br = new BufferedReader(isr)) {
                    transformer.getMainContext().read(extension.getName(), br, false);
                }
            } catch (IOException e) {
                e.printStackTrace();
                LOGGER.warn("Failed to set up the standard reversible access setter for {}!", extension.getName());
                continue;
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
                    modifiableClassLoader.loadModifier(extension.loader, codeModifierClass);
                }
                if (!extension.getMixinConfig().isEmpty()) {
                    final String mixinConfigFile = extension.getMixinConfig();
                    boolean added = false;
                    for (ASMTransformer transformer : modifiableClassLoader.getASMTransformers()) {
                        if (transformer instanceof ASMMixinTransformer) {
                            JSONObject mixinConfigJson;
                            try (InputStream is = extension.loader.getResourceAsStreamWithChildren(mixinConfigFile)) {
                                if (is == null) {
                                    throw new IOException("HierarchyClassLoader.getResourceAsStreamWithChildren(mixinConfigFile) yielded null.");
                                }
                                mixinConfigJson = new JSONObject(new String(JavaInterop.readAllBytes(is), StandardCharsets.UTF_8));
                            } catch (IOException e) {
                                throw new IOException("Cannot find mixin config " + mixinConfigFile + " in extension " + extension.getName(), e);
                            }
                            MixinConfig mixinConfig = MixinConfig.fromJson(mixinConfigJson);
                            ((ASMMixinTransformer) transformer).transformer.addMixin(extension.loader, mixinConfig);
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

        String id = ext.getDescription().getName().toLowerCase(Locale.ROOT);
        // remove from loaded extensions
        extensions.remove(id);
        extensionList.remove(ext);

        // remove class loader, required to reload the classes
        MinestomExtensionClassLoader classloader = this.extensionClassloaders.remove(id);
        try {
            // close resources
            if (classloader != null) {
                classloader.close();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }

        MinestomExtensionClassLoader modifierClassloader =  ext.getDescription().getOrigin().loader;
        if (modifierClassloader != null) {
            try {
                modifierClassloader.close();
            } catch (IOException e) {
                LOGGER.error("Unable to close extension codemodifier classloader", e);
            }
            ext.getDescription().getOrigin().loader = null;
        }
    }

    public void reload(String extensionName) {
        Extension ext = extensions.get(extensionName.toLowerCase());
        if (ext == null) {
            throw new IllegalArgumentException("Extension " + extensionName + " is not currently loaded.");
        }

        LOGGER.info("Reload extension {}", extensionName);
        List<String> dependents = new LinkedList<>(ext.getDescription().getDependents()); // copy dependents list
        List<List<URL>> originalURLsOfDependents = new LinkedList<>();

        for (String dependentID : dependents) {
            Extension dependentExt = extensions.get(dependentID.toLowerCase());
            originalURLsOfDependents.add(dependentExt.getDescription().getOrigin().files);

            LOGGER.info("Unloading dependent extension {} (because it depends on {})", dependentID, extensionName);
            unload(dependentExt);
        }

        LOGGER.info("Unloading extension {}", extensionName);
        unload(ext);

        System.gc();

        // ext and its dependents should no longer be referenced from now on

        // rediscover extension to reload. We allow dependency changes, so we need to fully reload it
        List<DiscoveredExtension> extensionsToReload = new LinkedList<>();
        LOGGER.info("Rediscovering extension {}", extensionName);
        DiscoveredExtension rediscoveredExtension = discoverFromURLs(ext.getDescription().getOrigin().files);
        extensionsToReload.add(rediscoveredExtension);

        for (List<URL> dependentUrls : originalURLsOfDependents) {
            // re-discover dependent extension to reload
            ExtensionManager.LOGGER.info("Rediscover dependent extension (depends on {})", extensionName);
            extensionsToReload.add(this.discoverFromURLs(dependentUrls));
        }

        // ensure correct order of dependencies
        this.loadExtensionList(extensionsToReload);
    }

    private boolean loadExtensionList(@NotNull List<DiscoveredExtension> extensionsToLoad) {
        // ensure correct order of dependencies
        this.generateLoadOrder(extensionsToLoad);

        for (DiscoveredExtension extension : extensionsToLoad) {
            if (extension.loader != null) {
                try {
                    extension.loader.close();
                } catch (IOException e) {
                    ExtensionManager.LOGGER.warn("Unable to close leftover classloader for extension {}", extension.getName(), e);
                }
                extension.loader = null;
            }
            if (extension.getLoadStatus() == LoadStatus.LOAD_SUCCESS) {
                extension.loader = this.newClassLoader(extension);
            }
        }

        this.setupAccessWideners(extensionsToLoad);
        // setup code modifiers for these extensions
        // TODO: it is possible that the new modifiers cannot be applied (because the targeted classes are already loaded), should we issue a warning?
        // If so, how?
        this.setupCodeModifiers(extensionsToLoad);

        List<Extension> newExtensions = new LinkedList<>();
        for (DiscoveredExtension toReload : extensionsToLoad) {
            // reload extensions
            LOGGER.info("Actually load extension {}", toReload.getName());
            Extension loadedExtension = this.attemptSingleLoad(toReload);
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
}
