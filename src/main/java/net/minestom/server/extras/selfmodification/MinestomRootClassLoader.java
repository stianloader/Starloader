package net.minestom.server.extras.selfmodification;

import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.reflect.InvocationTargetException;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.CodeSigner;
import java.security.CodeSource;
import java.security.cert.Certificate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.jetbrains.annotations.ApiStatus.AvailableSince;
import org.jetbrains.annotations.ApiStatus.Internal;
import org.jetbrains.annotations.ApiStatus.ScheduledForRemoval;
import org.jetbrains.annotations.CheckReturnValue;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Unmodifiable;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.util.CheckClassAdapter;
import org.objectweb.asm.util.TraceClassVisitor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import de.geolykt.starloader.transformers.ASMTransformer;
import de.geolykt.starloader.transformers.RawClassData;
import de.geolykt.starloader.transformers.TransformableClassloader;
import de.geolykt.starloader.util.JavaInterop;
import de.geolykt.starloader.util.OrderedCollection;

/**
 * Class Loader that can modify class bytecode when they are loaded.
 */
public class MinestomRootClassLoader extends HierarchyClassLoader implements TransformableClassloader {

    @Internal
    public static final boolean DEBUG = Boolean.getBoolean("classloader.debug");
    private static final boolean DUMP = MinestomRootClassLoader.DEBUG || Boolean.getBoolean("classloader.dump");

    private static MinestomRootClassLoader INSTANCE;

    @NotNull
    @Internal
    @AvailableSince(value = "4.0.0-a20240730")
    private static final ThreadLocal<Boolean> LOG_CLASSLOADING_FAILURES = Objects.requireNonNull(ThreadLocal.withInitial(() -> true));

    private static final Logger LOGGER = LoggerFactory.getLogger(MinestomRootClassLoader.class);

    @Deprecated
    @ScheduledForRemoval(inVersion = "5.0.0")
    private de.geolykt.starloader.deobf.access.AccessTransformInfo widener = new de.geolykt.starloader.deobf.access.AccessTransformInfo();

    /**
     * Classes that cannot be loaded/modified by this classloader.
     * Will go through parent class loader
     */
    private final Set<String> protectedClasses = ConcurrentHashMap.newKeySet();
    public final Set<String> protectedPackages = ConcurrentHashMap.newKeySet();

    {
        this.protectedClasses.add("de.geolykt.starloader.Starloader");
        this.protectedClasses.add("de.geolykt.starloader.UnlikelyEventException");
        this.protectedClasses.add("org.spongepowered.asm.mixin.injection.callback.CallbackInfo");
        this.protectedClasses.add("org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable");
        this.protectedClasses.add("org.spongepowered.asm.mixin.injection.callback.CancellationException");
        this.protectedPackages.add("org.objectweb.asm");
        this.protectedPackages.add("org.slf4j");
        this.protectedPackages.add("org.json");
        this.protectedPackages.add("net.minestom.server.extras.selfmodification"); // We do not want to load this package ourselves
        this.protectedPackages.add("de.geolykt.starloader.transformers");
        this.protectedPackages.add("de.geolykt.starloader.launcher");
        this.protectedPackages.add("de.geolykt.starloader.deobf.access");
        this.protectedPackages.add("de.geolykt.starloader.mod");
        this.protectedPackages.add("de.geolykt.starloader.util");
        this.protectedPackages.add("ch.qos.logback");
        this.protectedPackages.add("org.stianloader.micromixin.transform");
    }

    /**
     * Used to let ASM find out common super types, without actually committing to loading them
     * Otherwise ASM would accidentally load classes we might want to modify.
     */
    private final URLClassLoader asmClassLoader;

    @NotNull
    private final Collection<ASMTransformer> modifiers = new OrderedCollection<>();

    private MinestomRootClassLoader(ClassLoader parent) {
        super("Starloader Root ClassLoader", new URL[0], parent);
        this.asmClassLoader = this.newChild();
    }

    public static MinestomRootClassLoader getInstance() {
        if (MinestomRootClassLoader.INSTANCE == null) {
            synchronized (MinestomRootClassLoader.class) {
                if (MinestomRootClassLoader.INSTANCE == null) {
                    MinestomRootClassLoader.INSTANCE = new MinestomRootClassLoader(MinestomRootClassLoader.class.getClassLoader());
                }
            }
        }
        return MinestomRootClassLoader.INSTANCE;
    }

    @Override
    public Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
        Class<?> loadedClass = this.findLoadedClass(Objects.requireNonNull(name, "name must not be null"));
        if (loadedClass != null) {
            return loadedClass;
        }

        try {
            // we do not load system classes by ourselves
            ClassLoader loader = JavaInterop.getPlattformClassloader();
            if (loader != null) {
                Class<?> systemClass = loader.loadClass(name);
                MinestomRootClassLoader.LOGGER.trace("Loading system class: {}", systemClass);
                return systemClass;
            }
            throw new ClassNotFoundException("Java 9 " + (JavaInterop.isJava9() ? "capable " : "incapable") + " JavaInterop implementation refused to return the plattform classloader.");
        } catch (ClassNotFoundException e) {
            try {
                if (this.isProtected(name)) {
                    MinestomRootClassLoader.LOGGER.trace("Protected: {}", name);
                    return super.loadClass(name, resolve);
                }

                return define(name, resolve);
            } catch (Throwable ex) {
                MinestomRootClassLoader.LOGGER.trace("Failed to load class \""+ name + "\", resorting to parent loader. Code modifications forbidden. {}", ex);
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

    protected boolean isProtected(String name) {
        if (!this.protectedClasses.contains(name)) {
            for (String start : this.protectedPackages) {
                if (name.startsWith(start)) {
                    return true;
                }
            }
            return false;
        }
        return true;
    }

    @Override
    @Contract(pure = true)
    @AvailableSince(value = "4.0.0-a20240730")
    public boolean isThreadLoggingClassloadingFailures() {
        return MinestomRootClassLoader.LOG_CLASSLOADING_FAILURES.get();
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

            URL jarURL = rawClass.getSource();
            if (jarURL == null) {
                defined = defineClass(name, bytes, 0, bytes.length);
            } else {
                String path = jarURL.getPath();
                int seperatorIndex = path.lastIndexOf('!');
                if (seperatorIndex != -1) {
                    jarURL = new URL(path.substring(0, seperatorIndex));
                }
                defined = defineClass(name, bytes, 0, bytes.length, new CodeSource(jarURL, (CodeSigner[]) null));
            }

            MinestomRootClassLoader.LOGGER.trace("Loaded with code modifiers: {}", name);
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
                    MinestomRootClassLoader.LOGGER.trace("Loaded from child {}: {}", subloader, name);
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
        byte @NotNull[] originalBytes = JavaInterop.readAllBytes(input);
        input.close();
        byte @NotNull[] transformedBytes;
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

    synchronized byte @NotNull[] transformBytes(byte @NotNull[] classBytecode, @NotNull String qualifiedName) {
        if (!isProtected(qualifiedName)) {
            ClassReader reader = new ClassReader(classBytecode);
            ClassNode node = new ClassNode();
            boolean modified = false;

            reader.accept(node, 0);
            try {
                @SuppressWarnings("deprecation")
                boolean hack = this.widener.apply(node, true);
                modified = hack;
                synchronized (this.modifiers) {
                    Iterator<ASMTransformer> transformers = this.modifiers.iterator();
                    while (transformers.hasNext()) {
                        ASMTransformer transformer = transformers.next();
                        String internalName = node.name;
                        if (internalName == null) {
                            throw new NullPointerException();
                        }
                        if (MinestomRootClassLoader.DEBUG) {
                            MinestomRootClassLoader.LOGGER.info("{} could be able to transform {}", transformer.getClass().getSimpleName(), internalName);
                        }
                        if (transformer.isValidTarget(internalName) && transformer.accept(node)) {
                            if (MinestomRootClassLoader.DEBUG) {
                                MinestomRootClassLoader.LOGGER.info("{} was transformed by a {}", internalName, transformer.getClass().getSimpleName());
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
                if (this.isThreadLoggingClassloadingFailures()) {
                    MinestomRootClassLoader.LOGGER.error("Error within ASM transforming process. CLASS {} WILL NOT BE MODIFIED - THIS MAY BE LETHAL.", qualifiedName, t);
                }

                throw new RuntimeException("Error within ASM transforming process for class " + qualifiedName, t);
            }

            try {
                if (modified) {
                    ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_FRAMES) {
                        @Override
                        protected ClassLoader getClassLoader() {
                            return MinestomRootClassLoader.this.asmClassLoader;
                        }
                    };
                    node.accept(writer);
                    classBytecode = Objects.requireNonNull(writer.toByteArray());
                }
            } catch (Throwable t) {
                try {
                    StringWriter disassembledClass = new StringWriter();
                    TraceClassVisitor traceVisitor = new TraceClassVisitor(new PrintWriter(disassembledClass));
                    CheckClassAdapter checkAdapter = new CheckClassAdapter(Opcodes.ASM9, traceVisitor, true) {
                        @Override
                        public void visitInnerClass(String name, String outerName, String innerName, int access) {
                            super.visitInnerClass(name, outerName, innerName, access & ~Opcodes.ACC_SUPER);
                        }
                    };
                    node.accept(checkAdapter);

                    throw new RuntimeException("The class seems to be intact, but ASM does not like it anyways. In order to help on your debugging journey, take this:\n" + disassembledClass.toString());
                } catch (Throwable t0) {
                    if (t0 instanceof ThreadDeath) {
                        throw (ThreadDeath) t0;
                    } else if (t0 instanceof OutOfMemoryError) {
                        throw (OutOfMemoryError) t0;
                    }
                    t.addSuppressed(t0);
                }

                if (this.isThreadLoggingClassloadingFailures()) {
                    MinestomRootClassLoader.LOGGER.error("Unable to write ASM Classnode to bytecode for class '{}' (bork transformer?)", qualifiedName, t);
                }

                if (t instanceof ThreadDeath) {
                    throw (ThreadDeath) t;
                } else if (t instanceof OutOfMemoryError) {
                    throw (OutOfMemoryError) t;
                }

                throw new RuntimeException("Unable to write ASM Classnode to bytecode for class " + qualifiedName, t);
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
        return Objects.requireNonNull(URLClassLoader.newInstance(urls, this));
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
     * @deprecated 
     */
    @Deprecated
    @ScheduledForRemoval(inVersion = "5.0.0")
    public synchronized void addTransformer(ASMTransformer transformer) {
        this.addASMTransformer(Objects.requireNonNull(transformer));
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
     * @deprecated Use {@link TransformableClassloader#getASMTransformers()} instead.
     */
    @Deprecated
    @ScheduledForRemoval(inVersion = "5.0.0")
    public synchronized List<ASMTransformer> getTransformers() {
        synchronized (this.modifiers) {
            return new ArrayList<>(this.modifiers);
        }
    }

    @Deprecated
    @ScheduledForRemoval(inVersion = "5.0.0")
    public void readAccessWidener(@NotNull InputStream in) throws IOException {
        try (de.geolykt.starloader.deobf.access.AccessWidenerReader accessReader = new de.geolykt.starloader.deobf.access.AccessWidenerReader(widener, in, true)) {
            accessReader.readHeader();
            while (accessReader.readLn()) {
                // Continue reading
            }
        }
    }

    @Override
    @Contract(pure = false, value = "_ -> this")
    @AvailableSince(value = "4.0.0-a20240730")
    public MinestomRootClassLoader setThreadLoggingClassloadingFailures(boolean logFailures) {
        MinestomRootClassLoader.LOG_CLASSLOADING_FAILURES.set(logFailures);
        return this;
    }

    @Override
    @Contract(pure = false, mutates = "this")
    @AvailableSince(value = "4.0.0-a20231223")
    public void addASMTransformer(@NotNull ASMTransformer transformer) {
        synchronized (this.modifiers) {
            if (MinestomRootClassLoader.DEBUG) {
                MinestomRootClassLoader.LOGGER.info("Adding transformer {}", transformer.getClass().getName());
            }
            this.modifiers.add(transformer);
            if (MinestomRootClassLoader.DEBUG) {
                MinestomRootClassLoader.LOGGER.info("Currently registered transformers: ");
                for (ASMTransformer x : this.modifiers) {
                    MinestomRootClassLoader.LOGGER.info("  - {}", x.getClass().getName());
                }
            }
        }
    }

    @SuppressWarnings("null")
    @Override
    @NotNull
    @Unmodifiable
    @Contract(pure = true, value = "-> new")
    @AvailableSince(value = "4.0.0-a20231223")
    public Collection<@NotNull ASMTransformer> getASMTransformers() {
        synchronized (this.modifiers) {
            return Collections.unmodifiableCollection(new ArrayList<>(this.modifiers));
        }
    }

    @SuppressWarnings("null")
    @Override
    @NotNull
    @Contract(pure = false)
    @CheckReturnValue
    @AvailableSince(value = "4.0.0-a20231223")
    public Class<?> transformAndDefineClass(@NotNull String className, @NotNull RawClassData data) {
        if (MinestomRootClassLoader.DEBUG) {
            MinestomRootClassLoader.LOGGER.info("Forcefully defining class '{}'", className);
        }

        byte[] transformed = this.transformBytes(data.getBytes(), className);
        URL jarURL = data.getSource();

        if (MinestomRootClassLoader.DUMP) {
            try {
                Path parent = Paths.get("classes", className.replace('.', '/')).getParent();
                if (parent != null) {
                    Files.createDirectories(parent);
                }
                Files.write(Paths.get("classes", className.replace('.', '/') + ".class"), transformed);
            } catch (IOException e) {
                MinestomRootClassLoader.LOGGER.info("Unable to dump forcefully defined class '{}'", className, e);
            }
        }

        if (jarURL == null) {
            return super.defineClass(className, transformed, 0, transformed.length);
        } else {
            String path = jarURL.getPath();
            int seperatorIndex = path.lastIndexOf('!');
            if (seperatorIndex != -1) {
                try {
                    jarURL = new URL(path.substring(0, seperatorIndex));
                } catch (MalformedURLException e) {
                    MinestomRootClassLoader.LOGGER.warn("Bumped into a MalformedURLException while forcefully defining a class", e);
                }
            }
            return super.defineClass(className, transformed, 0, transformed.length, new CodeSource(jarURL, (Certificate[]) null));
        }
    }
}
