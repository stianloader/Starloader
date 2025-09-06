package de.geolykt.starloader.launcher.service;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.ListIterator;
import java.util.Objects;
import java.util.concurrent.Callable;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.ClassNode;
import org.slf4j.LoggerFactory;
import org.spongepowered.asm.launch.platform.container.ContainerHandleVirtual;
import org.spongepowered.asm.launch.platform.container.IContainerHandle;
import org.spongepowered.asm.logging.ILogger;
import org.spongepowered.asm.mixin.MixinEnvironment.Phase;
import org.spongepowered.asm.service.IClassBytecodeProvider;
import org.spongepowered.asm.service.IClassProvider;
import org.spongepowered.asm.service.IClassTracker;
import org.spongepowered.asm.service.IMixinAuditTrail;
import org.spongepowered.asm.service.IMixinInternal;
import org.spongepowered.asm.service.ITransformerProvider;
import org.spongepowered.asm.service.MixinServiceAbstract;
import org.spongepowered.asm.util.IConsumer;

import net.minestom.server.extras.selfmodification.MinestomRootClassLoader;

import de.geolykt.starloader.util.JavaInterop;

public class SLMixinService extends MixinServiceAbstract {

    private static final MinestomRootClassLoader CLASSLOADER = MinestomRootClassLoader.getInstance();

    private static SLMixinService instance;
    public static SLMixinService getInstance() {
        return SLMixinService.instance;
    }

    private final IClassBytecodeProvider bytecodeProvider = new IClassBytecodeProvider() {
        @Override
        public ClassNode getClassNode(String name) throws ClassNotFoundException, IOException {
            return this.getClassNode(name, false);
        }

        @Override
        public ClassNode getClassNode(String name, boolean runTransformers) throws ClassNotFoundException, IOException {
            return this.getClassNode(name, runTransformers, 0);
        }

        @Override
        public ClassNode getClassNode(String name, boolean runTransformers, int readerFlags) throws ClassNotFoundException, IOException {
            List<Exception> caughtExceptions = new ArrayList<>();

            @SuppressWarnings("unchecked")
            Callable<@NotNull ClassReader>[] suppliers = new Callable[4];

            int i = 0;
            int systemClassLoaderIndex;
            if (JavaInterop.isJava9() || SLMixinService.CLASSLOADER.isProtected(name)) {
                systemClassLoaderIndex = i++;
            } else {
                systemClassLoaderIndex = suppliers.length - 1;
            }

            suppliers[systemClassLoaderIndex] = () -> {
                ClassLoader cl = JavaInterop.getPlatformClassLoader();
                InputStream is;
                if (cl == null) {
                   is = ClassLoader.getSystemResourceAsStream(name.replace('.', '/') + ".class");
                } else {
                    is = cl.getResourceAsStream(name.replace('.', '/') + ".class");
                }
                return new ClassReader(is);
            };

            suppliers[i++] = () -> {
                return new ClassReader(SLMixinService.CLASSLOADER.loadClassBytes(name, false).getBytes());
            };

            suppliers[i++] = () -> {
                return new ClassReader(SLMixinService.CLASSLOADER.loadBytesWithChildren(name, false));
            };

            suppliers[i++] = () -> {
                return new ClassReader(SLMixinService.CLASSLOADER.getResourceAsStream(name.replace('.', '/') + ".class"));
            };

            for (Callable<@NotNull ClassReader> supplier : suppliers) {
                try {
                    @SuppressWarnings("null")
                    ClassReader reader = supplier.call();
                    ClassNode node = new ClassNode();
                    reader.accept(node, readerFlags);
                    return node;
                } catch (Exception e) {
                    caughtExceptions.add(e);
                }
            }

            Exception causedBy;
            ListIterator<Exception> it = caughtExceptions.listIterator(caughtExceptions.size());
            if (it.hasPrevious()) {
                causedBy = it.previous();
                while (it.hasPrevious()) {
                    causedBy.addSuppressed(it.previous());
                }
            } else {
                causedBy = null;
            }

            ClassNotFoundException thrownException = new ClassNotFoundException("Could not load ClassNode with name " + name, causedBy);

            if (MinestomRootClassLoader.DEBUG) {
                thrownException.fillInStackTrace();
                LoggerFactory.getLogger(SLMixinService.class).warn("Unable to call #getClassNode(): Couldn't load ClassNode for class with name '{}'.", name, thrownException);
            }

            throw thrownException;
        }
    };

    private final IClassProvider classprovider = new IClassProvider() {

        @Override
        public Class<?> findAgentClass(String name, boolean initialize) throws ClassNotFoundException {
            return this.findAgentClass(name, initialize);
        }

        @Override
        public Class<?> findClass(String name) throws ClassNotFoundException {
            try {
                return SLMixinService.CLASSLOADER.findClass(name);
            } catch (ClassNotFoundException cnfe) {
                if (MinestomRootClassLoader.DEBUG) {
                    LoggerFactory.getLogger(SLMixinService.class).warn("#findClass(String): Unable to find class '{}'", name, cnfe);
                }
                throw cnfe;
            }
        }

        @Override
        public Class<?> findClass(String name, boolean initialize) throws ClassNotFoundException {
            try {
                return Class.forName(name, initialize, Thread.currentThread().getContextClassLoader());
            } catch (ClassNotFoundException cnfe) {
                try {
                    return Class.forName(name, initialize, SLMixinService.class.getClassLoader());
                } catch (ClassNotFoundException cnfe2) {
                    cnfe2.addSuppressed(cnfe);
                    if (MinestomRootClassLoader.DEBUG) {
                        LoggerFactory.getLogger(SLMixinService.class).warn("#findClass(String, boolean): Unable to find class '{}'", name, cnfe2);
                    }
                    throw cnfe2;
                }
            }
        }

        @Override
        public URL[] getClassPath() {
            return SLMixinService.CLASSLOADER.getURLs();
        }
    };

    private IConsumer<Phase> wiredPhaseConsumer;

    @Override
    protected ILogger createLogger(String name) {
        return new SLMixinLogger(Objects.requireNonNull(name, "logger may not have a null name"));
    }

    @Override
    public IMixinAuditTrail getAuditTrail() {
        return null; // unsupported
    }

    @Override
    public IClassBytecodeProvider getBytecodeProvider() {
        return this.bytecodeProvider;
    }

    @Override
    public IClassProvider getClassProvider() {
        return this.classprovider;
    }

    @Override
    public IClassTracker getClassTracker() {
        return null; // unsupported
    }

    @Nullable
    public final <T extends IMixinInternal> T getMixinInternal(Class<T> type) {
        return this.getInternal(type);
    }

    @Override
    public String getName() {
        return "Starloader Bootstrap";
    }

    public IConsumer<Phase> getPhaseConsumer() {
        return this.wiredPhaseConsumer;
    }

    @Override
    public Collection<String> getPlatformAgents() {
        return Arrays.asList("de.geolykt.starloader.launcher.service.SLPlattformAgent");
    }

    @Override
    public IContainerHandle getPrimaryContainer() {
        return new ContainerHandleVirtual(this.getName());
    }

    @Override
    public InputStream getResourceAsStream(String name) {
        return SLMixinService.CLASSLOADER.getResourceAsStreamWithChildren(Objects.requireNonNull(name, "name may not be null"));
    }

    @Override
    public ITransformerProvider getTransformerProvider() {
        return null; // unsupported
    }

    @Override
    public void init() {
        SLMixinService.instance = this;
        super.init();
    }

    @Override
    public boolean isValid() {
        return true;
    }

    @Override
    @Deprecated
    public void unwire() {
        super.unwire();
        this.wiredPhaseConsumer = null;
    }

    @Override
    @Deprecated
    public void wire(Phase phase, IConsumer<Phase> phaseConsumer) {
        super.wire(phase, phaseConsumer);
        this.wiredPhaseConsumer = phaseConsumer;
    }
}
