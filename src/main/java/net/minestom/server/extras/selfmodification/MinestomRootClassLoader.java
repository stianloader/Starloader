package net.minestom.server.extras.selfmodification;

import org.jetbrains.annotations.NotNull;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import de.geolykt.starloader.transformers.ASMTransformer;
import net.fabricmc.accesswidener.AccessWidener;
import net.fabricmc.accesswidener.AccessWidenerVisitor;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.InvocationTargetException;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

/**
 * Class Loader that can modify class bytecode when they are loaded
 */
public class MinestomRootClassLoader extends HierarchyClassLoader {

    public final static Logger LOGGER = LoggerFactory.getLogger(MinestomRootClassLoader.class);

    private static MinestomRootClassLoader INSTANCE;

    public AccessWidener widener;

    /**
     * Classes that cannot be loaded/modified by this classloader.
     * Will go through parent class loader
     */
    public final Set<String> protectedClasses = new HashSet<>() {
        private static final long serialVersionUID = 1562431043013365680L;

        {
            add("net.minestom.server.extras.selfmodification.CodeModifier");
            add("net.minestom.server.extras.selfmodification.MinestomOverwriteClassLoader");
        }
    };

    public final Set<String> protectedPackages = new HashSet<>() {
        private static final long serialVersionUID = -4833006816182792038L;

        {
            add("com.google");
            add("org.objectweb.asm");
            add("org.slf4j");
            add("org.apache");
            add("org.spongepowered");
            add("net.minestom.server.extras.selfmodification"); // We do not want to load this package ourselves
            add("org.jboss.shrinkwrap.resolver");
            add("kotlin");
            add("net.fabricmc.accesswidener"); // this package will throw a linkage error too when loaded otherwise
            add("de.geolykt.starloader.transformers");
            add("de.geolykt.starloader.launcher");
            add("de.geolykt.starloader.mod");
        }
    };

    /**
     * Used to let ASM find out common super types, without actually committing to loading them
     * Otherwise ASM would accidentally load classes we might want to modify
     */
    private final URLClassLoader asmClassLoader;

    // TODO: replace by tree to optimize lookup times. We can use the fact that package names are split with '.' to allow for fast lookup
    // TODO: eg. Node("java", Node("lang"), Node("io")). Loading "java.nio.Channel" would apply modifiers from "java", but not "java.io" or "java.lang".
    // TODO: that's an example, please don't modify standard library classes. And this classloader should not let you do it because it first asks the platform classloader

    // TODO: priorities?
    private final List<ASMTransformer> modifiers = new LinkedList<>();

    private MinestomRootClassLoader(ClassLoader parent) {
        super("Starloader Root ClassLoader", extractURLsFromClasspath(), parent);
        asmClassLoader = newChild(new URL[0]);
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
        Class<?> loadedClass = findLoadedClass(name);
        if (loadedClass != null)
            return loadedClass;

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
                LOGGER.trace("Failed to load class, resorting to parent loader: " + name, ex);
                // fail to load class, let parent load
                // this forbids code modification, but at least it will load
                return super.loadClass(name, resolve);
            }
        }
    }

    private boolean isProtected(String name) {
        if (!protectedClasses.contains(name)) {
            for (String start : protectedPackages) {
                if (name.startsWith(start))
                    return true;
            }
            return false;
        }
        return true;
    }

    private Class<?> define(String name, boolean resolve) throws IOException, ClassNotFoundException {
        try {
            byte[] bytes = loadBytes(name, true);
            Class<?> defined = defineClass(name, bytes, 0, bytes.length);
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
     * @return The transformed bytes
     * @throws IOException
     * @throws ClassNotFoundException
     */
    public byte[] loadBytes(String name, boolean transform) throws IOException, ClassNotFoundException {
        if (name == null)
            throw new ClassNotFoundException();
        String path = name.replace(".", "/") + ".class";
        InputStream input = getResourceAsStream(path);
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

    public byte[] loadBytesWithChildren(String name, boolean transform) throws IOException, ClassNotFoundException {
        if (name == null)
            throw new ClassNotFoundException();
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

    byte[] transformBytes(byte[] classBytecode, @NotNull String qualifiedName) {
        if (!isProtected(qualifiedName)) {
            ClassReader reader = new ClassReader(classBytecode);
            ClassNode node = new ClassNode();
            boolean modified = false;
            if (widener != null && widener.getTargets().contains(qualifiedName)) {
                ClassVisitor visitor = AccessWidenerVisitor.createClassVisitor(Opcodes.ASM9, node, widener);
                reader.accept(visitor, 0);
                modified = true;
            } else {
                reader.accept(node, 0);
            }
            try {
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
                LOGGER.error("Error within ASM transforming process. CLASS WILL NOT BE MODIFIED - THIS MAY BE LETHAL.", t);
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

    // overriden to increase access (from protected to public)
    @Override
    public Class<?> findClass(String name) throws ClassNotFoundException {
        return super.findClass(name);
    }

    @NotNull
    public URLClassLoader newChild(@NotNull URL[] urls) {
        return URLClassLoader.newInstance(urls, this);
    }

    public void loadModifier(File[] originFiles, String codeModifierClass) {
        URL[] urls = new URL[originFiles.length];
        try {
            for (int i = 0; i < originFiles.length; i++) {
                urls[i] = originFiles[i].toURI().toURL();
            }
            @SuppressWarnings("resource")
            URLClassLoader loader = newChild(urls);
            Class<?> modifierClass = loader.loadClass(codeModifierClass);
            if (ASMTransformer.class.isAssignableFrom(modifierClass)) {
                ASMTransformer modifier = (ASMTransformer) modifierClass.getDeclaredConstructor().newInstance();
                synchronized (modifiers) {
                    LOGGER.warn("Added ASM Transformer: {}", modifier);
                    addTransformer(modifier);
                }
            }
        } catch (MalformedURLException | ClassNotFoundException | InvocationTargetException | InstantiationException | IllegalAccessException | NoSuchMethodException e) {
            e.printStackTrace();
        }
    }

    /**
     * Adds a transformer to the transformer pool
     *
     * @param transformer The transformer to add
     * @since 2.1.0
     */
    public void addTransformer(ASMTransformer transformer) {
        synchronized (modifiers) {
            modifiers.add(transformer);
        }
    }

    /**
     * Adds a modifier to the modifier pool
     *
     * @param modifier The modifier to add
     * @deprecated {@link CodeModifier} is getting phased out, use {@link ASMTransformer} instead. Replaced by {@link #addTransformer(ASMTransformer)}.
     */
    @Deprecated(forRemoval = true, since = "2.1.0")
    public void addCodeModifier(CodeModifier modifier) {
        this.addTransformer(modifier);
    }

    @Override
    public void addURL(URL url) {
        super.addURL(url);
    }

    /**
     * Obtains the modifiers currently in use.
     *
     * @return the modifiers
     * @deprecated While this method exposes the modifiers as {@link CodeModifier}, it internally uses {@link ASMTransformer}. This will lead to death and descruction later on.
     */
    @Deprecated(forRemoval = true, since = "2.1.0")
    @SuppressWarnings({ "rawtypes", "unchecked" })
    public List<CodeModifier> getModifiers() {
        return (List) modifiers;
    }

    /**
     * Obtains a clone of the list of ASM Transformers currently in use.
     *
     * @return The ASM transformers in use.
     * @since 2.1.0
     */
    public List<ASMTransformer> getTransformers() {
        return new ArrayList<>(modifiers);
    }
}
