package net.minestom.server.extras.selfmodification;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.InvocationTargetException;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.security.CodeSigner;
import java.security.CodeSource;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;

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

/**
 * Class Loader that can modify class bytecode when they are loaded.
 */
public class MinestomRootClassLoader extends HierarchyClassLoader {

    private static final Logger LOGGER = LoggerFactory.getLogger(MinestomRootClassLoader.class);

    private static MinestomRootClassLoader INSTANCE;

    private AccessTransformInfo widener = new AccessTransformInfo();

    /**
     * Classes that cannot be loaded/modified by this classloader.
     * Will go through parent class loader
     */
    private final Set<String> protectedClasses = new HashSet<>() {
        private static final long serialVersionUID = 1562431043013365680L;

        {
            add("net.minestom.server.extras.selfmodification.CodeModifier");
            add("net.minestom.server.extras.selfmodification.MinestomOverwriteClassLoader");
        }
    };

    public final Set<String> protectedPackages = new CopyOnWriteArraySet<>() {
        private static final long serialVersionUID = -4833006816182792038L;

        {
            add("com.google");
            add("org.objectweb.asm");
            add("org.slf4j");
            add("org.apache");
            add("org.spongepowered");
            add("org.json");
            add("net.minestom.server.extras.selfmodification"); // We do not want to load this package ourselves
            add("de.geolykt.starloader.transformers");
            add("de.geolykt.starloader.launcher");
            add("de.geolykt.starloader.deobf.access");
            add("de.geolykt.starloader.mod");
            add("de.geolykt.starloader.util");
            add("ch.qos.logback");
        }
    };

    /**
     * Used to let ASM find out common super types, without actually committing to loading them
     * Otherwise ASM would accidentally load classes we might want to modify.
     */
    private final URLClassLoader asmClassLoader;

    // TODO: priorities?
    // TODO: make concurrent
    private final List<ASMTransformer> modifiers = new LinkedList<>();

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
        String[] parts = classpath.split(";");
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
            Class<?> systemClass = ClassLoader.getPlatformClassLoader().loadClass(name);
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
                // FIXME this state is always achieved if classes from extensions are loaded.
                // While apparently this will not cause all too much issues, it might still be dangerous.
                LOGGER.debug("Failed to load class \""+ name + "\", resorting to parent loader. Code modifications forbidden.", ex);
                // fail to load class, let parent load
                // this forbids code modification, but at least it will load
                return super.loadClass(name, resolve);
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
                String path = rawClass.getSource().getPath();
                URL jarURL = new URL(path.substring(0, path.lastIndexOf('!')));
                defined = defineClass(name, bytes, 0, bytes.length, new CodeSource(jarURL, (CodeSigner[]) null));
            }

            LOGGER.trace("Loaded with code modifiers: {}", name);
            if (resolve) {
                resolveClass(defined);
            }
            return defined;
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
        byte[] originalBytes = input.readAllBytes();
        input.close();
        byte[] transformedBytes;
        if (transform) {
            transformedBytes = transformBytes(originalBytes, name);
        } else {
            transformedBytes = originalBytes;
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
        byte[] originalBytes = input.readAllBytes();
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
                        if (transformer.isValidTraget(internalName) && transformer.accept(node)) {
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
    private URLClassLoader newChild(@NotNull URL... urls) {
        URLClassLoader instance = URLClassLoader.newInstance(urls, this);
        if (instance == null) {
            throw new IllegalStateException();
        }
        return instance;
    }

    public void loadModifier(URL[] originFiles, String codeModifierClass) {
        try {
            @SuppressWarnings({ "resource", "null" })
            URLClassLoader loader = newChild(originFiles);
            Class<?> modifierClass = loader.loadClass(codeModifierClass);
            if (ASMTransformer.class.isAssignableFrom(modifierClass)) {
                ASMTransformer modifier = (ASMTransformer) modifierClass.getDeclaredConstructor().newInstance();
                LOGGER.warn("Added ASM Transformer: {}", modifier);
                addTransformer(modifier);
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
            modifiers.add(transformer);
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
