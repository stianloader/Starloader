package net.minestom.server.extras.selfmodification;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.InvocationTargetException;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.CodeSigner;
import java.security.CodeSource;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.jetbrains.annotations.ApiStatus.Internal;
import org.jetbrains.annotations.NotNull;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.tree.ClassNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import de.geolykt.starloader.deobf.access.AccessTransformInfo;
import de.geolykt.starloader.deobf.access.AccessWidenerReader;
import de.geolykt.starloader.transformers.ASMTransformer;
import de.geolykt.starloader.transformers.RawClassData;
import de.geolykt.starloader.util.JavaInterop;
import de.geolykt.starloader.util.OrderedCollection;

/**
 * Class Loader that can modify class bytecode when they are loaded.
 */
public class MinestomRootClassLoader extends HierarchyClassLoader {

    private static final Logger LOGGER = LoggerFactory.getLogger(MinestomRootClassLoader.class);
    private static final boolean DEBUG = Boolean.getBoolean("classloader.debug");
    private static final boolean DUMP = DEBUG || Boolean.getBoolean("classloader.dump");
    @SuppressWarnings("null") // Yes it can be null, but "path.seperator" should always be present - in theory
    @NotNull
    private static final String PATH_SEPARATOR = System.getProperty("path.separator");

    private static MinestomRootClassLoader INSTANCE;

    private AccessTransformInfo widener = new AccessTransformInfo();

    /**
     * Classes that cannot be loaded/modified by this classloader.
     * Will go through parent class loader
     */
    private final Set<String> protectedClasses = ConcurrentHashMap.newKeySet();
    public final Set<String> protectedPackages = ConcurrentHashMap.newKeySet();

    {
        this.protectedClasses.add("de.geolykt.starloader.Starloader");
        this.protectedClasses.add("de.geolykt.starloader.UnlikelyEventException");
        this.protectedPackages.add("com.google");
        this.protectedPackages.add("org.objectweb.asm");
        this.protectedPackages.add("org.slf4j");
        this.protectedPackages.add("org.spongepowered");
        this.protectedPackages.add("org.json");
        this.protectedPackages.add("net.minestom.server.extras.selfmodification"); // We do not want to load this package ourselves
        this.protectedPackages.add("de.geolykt.starloader.transformers");
        this.protectedPackages.add("de.geolykt.starloader.launcher");
        this.protectedPackages.add("de.geolykt.starloader.deobf.access");
        this.protectedPackages.add("de.geolykt.starloader.mod");
        this.protectedPackages.add("de.geolykt.starloader.util");
        this.protectedPackages.add("ch.qos.logback");
    }

    /**
     * Used to let ASM find out common super types, without actually committing to loading them
     * Otherwise ASM would accidentally load classes we might want to modify.
     */
    private final URLClassLoader asmClassLoader;

    private final Collection<ASMTransformer> modifiers = new OrderedCollection<>();

    private MinestomRootClassLoader(ClassLoader parent) {
        super("Starloader Root ClassLoader", extractURLsFromClasspath(), parent);
        asmClassLoader = newChild();
    }

    public static MinestomRootClassLoader getInstance() {
        if (INSTANCE == null) {
            synchronized (MinestomRootClassLoader.class) {
                if (INSTANCE == null) {
                    INSTANCE = new MinestomRootClassLoader(MinestomRootClassLoader.class.getClassLoader());
                }
            }
        }
        return INSTANCE;
    }

    private static URL[] extractURLsFromClasspath() {
        String classpath = System.getProperty("java.class.path");
        String[] parts = classpath.split(PATH_SEPARATOR);
        URL[] urls = new URL[parts.length];
        for (int i = 0; i < urls.length; i++) {
            try {
                String part = parts[i];
                String protocol;
                if (part.contains("!")) {
                    protocol = "jar://";
                } else {
                    protocol = "file://";
                }
                urls[i] = new URL(protocol + part);
            } catch (MalformedURLException e) {
                throw new Error(e);
            }
        }
        return urls;
    }

    @Override
    public Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
        Class<?> loadedClass = findLoadedClass(Objects.requireNonNull(name, "name must not be null"));
        if (loadedClass != null) {
            return loadedClass;
        }

        try {
            // we do not load system classes by ourselves
            Class<?> systemClass = JavaInterop.getPlattformClassloader().loadClass(name);
            LOGGER.trace("System class: {}", systemClass);
            return systemClass;
        } catch (ClassNotFoundException e) {
            try {
                if (isProtected(name)) {
                    LOGGER.trace("Protected: {}", name);
                    return super.loadClass(name, resolve);
                }

                return define(name, resolve);
            } catch (Throwable ex) {
                LOGGER.trace("Failed to load class \""+ name + "\", resorting to parent loader. Code modifications forbidden. {}", ex);
                // fail to load class, let parent load
                // this forbids code modification, but at least it will load
                try {
                    return super.loadClass(name, resolve);
                } catch (ClassNotFoundException cnfe) {
                    cnfe.addSuppressed(ex);
                    throw cnfe;
                }
            }
        }
    }

    private boolean isProtected(String name) {
        if (!protectedClasses.contains(name)) {
            for (String start : protectedPackages) {
                if (name.startsWith(start)) {
                    return true;
                }
            }
            return false;
        }
        return true;
    }

    private Class<?> define(String name, boolean resolve) throws IOException, ClassNotFoundException {
        try {
            RawClassData rawClass;
            try {
                rawClass = loadClassBytes(name, true);
            } catch (Throwable t) {
                throw new ClassNotFoundException("Unable to load bytes", t);
            }
            Class<?> defined;
            byte[] bytes = rawClass.getBytes();

            if (rawClass.getSource() == null) {
                defined = defineClass(name, bytes, 0, bytes.length);
            } else {
                URL jarURL = rawClass.getSource();
                String path = jarURL.getPath();
                int seperatorIndex = path.lastIndexOf('!');
                if (seperatorIndex != -1) {
                    jarURL = new URL(path.substring(0, seperatorIndex));
                }
                defined = defineClass(name, bytes, 0, bytes.length, new CodeSource(jarURL, (CodeSigner[]) null));
            }

            LOGGER.trace("Loaded with code modifiers: {}", name);
            if (resolve) {
                resolveClass(defined);
            }
            return defined;
        } catch (LinkageError e) {
            // Well we did hit the right classloader (so no need to check children), but it did not produce the right output
            throw new ClassNotFoundException("Invalid bytecode for class " + name, e);
        } catch (ClassNotFoundException e) {
            // could not load inside this classloader, attempt with children
            Class<?> defined = null;
            for (MinestomExtensionClassLoader subloader : children) {
                try {
                    defined = subloader.loadClassAsChild(name, resolve);
                    LOGGER.trace("Loaded from child {}: {}", subloader, name);
                    return defined;
                } catch (ClassNotFoundException e1) {
                    // not found inside this child, move on to next
                    e.addSuppressed(e1);
                }
            }
            throw e;
        }
    }

    /**
     * Loads and possibly transforms class bytecode corresponding to the given binary name.
     *
     * @param name
     * @param transform
     * @return The transformed bytes attached with the URL the bytes were loaded from.
     * @throws IOException
     * @throws ClassNotFoundException
     * @since 3.1.0
     */
    public RawClassData loadClassBytes(String name, boolean transform) throws IOException, ClassNotFoundException {
        if (name == null) {
            throw new ClassNotFoundException("Name may not be null.");
        }
        String path = name.replace(".", "/") + ".class";
        URL url = findResource(path);
        InputStream input;
        if (url == null) {
            input = getResourceAsStream(name);
        } else {
            input = url.openStream();
        }
        if (input == null) {
            throw new ClassNotFoundException("Could not find resource " + path);
        }
        byte[] originalBytes = JavaInterop.readAllBytes(input);
        input.close();
        byte[] transformedBytes;
        if (transform) {
            transformedBytes = transformBytes(originalBytes, name);
        } else {
            transformedBytes = originalBytes;
        }

        if (DUMP) {
            Path parent = Paths.get("classes", path).getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            Files.write(Paths.get("classes", path), transformedBytes);
        }

        return new RawClassData(url, transformedBytes);
    }

    public byte[] loadBytesWithChildren(String name, boolean transform) throws IOException, ClassNotFoundException {
        if (name == null) {
            throw new ClassNotFoundException();
        }
        String path = name.replace(".", "/") + ".class";
        InputStream input = getResourceAsStreamWithChildren(path);
        if (input == null) {
            throw new ClassNotFoundException("Could not find resource " + path);
        }
        byte[] originalBytes = JavaInterop.readAllBytes(input);
        input.close();
        if (transform) {
            return transformBytes(originalBytes, name);
        }
        return originalBytes;
    }

    synchronized byte[] transformBytes(byte[] classBytecode, @NotNull String qualifiedName) {
        if (!isProtected(qualifiedName)) {
            ClassReader reader = new ClassReader(classBytecode);
            ClassNode node = new ClassNode();
            boolean modified = false;

            reader.accept(node, 0);
            try {
                modified = widener.apply(node, true);
                synchronized (modifiers) {
                    Iterator<ASMTransformer> transformers = modifiers.iterator();
                    while (transformers.hasNext()) {
                        ASMTransformer transformer = transformers.next();
                        String internalName = node.name;
                        if (internalName == null) {
                            throw new NullPointerException();
                        }
                        if (DEBUG) {
                            LOGGER.info("{} could be able to transform {}", transformer.getClass().getSimpleName(), internalName);
                        }
                        if (transformer.isValidTarget(internalName) && transformer.accept(node)) {
                            if (DEBUG) {
                                LOGGER.info("{} was transformed by a {}", internalName, transformer.getClass().getSimpleName());
                            }
                            if (!transformer.isValid()) {
                                transformers.remove();
                            }
                            modified = true;
                        }
                    }
                }
            } catch (Throwable t) {
                // Apparently errors would get absorbed otherwise.
                LOGGER.error("Error within ASM transforming process. CLASS {} WILL NOT BE MODIFIED - THIS MAY BE LETHAL.", qualifiedName, t);
                throw new RuntimeException("Error within ASM transforming process.", t);
            }
            try {
                if (modified) {
                    ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_FRAMES) {
                        @Override
                        protected ClassLoader getClassLoader() {
                            return asmClassLoader;
                        }
                    };
                    node.accept(writer);
                    classBytecode = writer.toByteArray();
                    LOGGER.trace("Modified {}", qualifiedName);
                }
            } catch (Throwable t) {
                LOGGER.error("Unable to write ASM Classnode to bytecode (bork transformer?)", t);
                throw new RuntimeException("Unable to write ASM Classnode to bytecode", t);
            }
        }
        return classBytecode;
    }

    // Overridden to increase access (from protected to public)
    @Override
    public Class<?> findClass(String name) throws ClassNotFoundException {
        return super.findClass(name);
    }

    @NotNull
    @Internal
    public URLClassLoader newChild(@NotNull URL... urls) {
        URLClassLoader instance = URLClassLoader.newInstance(urls, this);
        if (instance == null) {
            throw new IllegalStateException();
        }
        return instance;
    }

    @Internal
    public void loadModifier(ClassLoader modifierLoader, String codeModifierClass) {
        try {
            Class<?> modifierClass = modifierLoader.loadClass(codeModifierClass);
            if (ASMTransformer.class.isAssignableFrom(modifierClass)) {
                addTransformer((ASMTransformer) modifierClass.getDeclaredConstructor().newInstance());
            }
        } catch (ClassNotFoundException | InvocationTargetException | InstantiationException | IllegalAccessException | NoSuchMethodException e) {
            e.printStackTrace();
        }
    }

    /**
     * Adds a transformer to the transformer pool.
     *
     * @param transformer The transformer to add
     * @since 2.1.0
     */
    public synchronized void addTransformer(ASMTransformer transformer) {
        synchronized (modifiers) {
            if (DEBUG) {
                LOGGER.info("Adding transformer {}", transformer.getClass().getName());
            }
            modifiers.add(transformer);
            if (DEBUG) {
                LOGGER.info("Currently registered transformers: ");
                for (ASMTransformer x :modifiers) {
                    LOGGER.info("  - {}", x.getClass().getName());
                }
            }
        }
    }

    @Override
    public void addURL(URL url) {
        super.addURL(url);
    }

    /**
     * Obtains a clone of the list of ASM Transformers currently in use.
     *
     * @return The ASM transformers in use.
     * @since 2.1.0
     */
    public synchronized List<ASMTransformer> getTransformers() {
        synchronized (modifiers) {
            return new ArrayList<>(modifiers);
        }
    }

    public void readAccessWidener(@NotNull InputStream in) throws IOException {
        try (AccessWidenerReader accessReader = new AccessWidenerReader(widener, in, true)) {
            accessReader.readHeader();
            while (accessReader.readLn()) {
                // Continue reading
            }
        }
    }
}
