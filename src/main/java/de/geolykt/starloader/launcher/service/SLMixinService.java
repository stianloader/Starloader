package de.geolykt.starloader.launcher.service;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.Arrays;
import java.util.Collection;
import java.util.Objects;

import org.jetbrains.annotations.Nullable;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.ClassNode;
import org.slf4j.LoggerFactory;
import org.spongepowered.asm.launch.platform.container.ContainerHandleVirtual;
import org.spongepowered.asm.launch.platform.container.IContainerHandle;
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

    private static SLMixinService instance;

    private static final MinestomRootClassLoader CLASSLOADER = MinestomRootClassLoader.getInstance();
    private IConsumer<Phase> wiredPhaseConsumer;

    private final IClassProvider classprovider = new IClassProvider() {

        @Override
        public URL[] getClassPath() {
            return SLMixinService.CLASSLOADER.getURLs();
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
    };

    private final IClassBytecodeProvider bytecodeProvider = new IClassBytecodeProvider() {
        @Override
        public ClassNode getClassNode(String name, boolean runTransformers) throws ClassNotFoundException, IOException {
            return this.getClassNode(name, runTransformers, 0);
        }

        @Override
        public ClassNode getClassNode(String name) throws ClassNotFoundException, IOException {
            return this.getClassNode(name, false);
        }

        @Override
        public ClassNode getClassNode(String name, boolean runTransformers, int readerFlags) throws ClassNotFoundException, IOException {
            ClassNode node = new ClassNode();
            ClassReader reader;

            try {
                ClassLoader cl = JavaInterop.getPlattformClassloader();
                InputStream is;
                if (cl == null) {
                   is = ClassLoader.getSystemResourceAsStream(name.replace('.', '/') + ".class");
                } else {
                    is = cl.getResourceAsStream(name.replace('.', '/') + ".class");
                }
                reader = new ClassReader(is);
            } catch (Exception e) {
                try {
                    reader = new ClassReader(SLMixinService.CLASSLOADER.loadClassBytes(name, false).getBytes());
                } catch (Exception e2) {
                    try {
                        reader = new ClassReader(SLMixinService.CLASSLOADER.loadBytesWithChildren(name, false));
                    } catch (Exception e3) {
                        // Final hail-mary. It'll not be transformed but it should find it now with all the edge cases
                        try {
                            reader = new ClassReader(SLMixinService.CLASSLOADER.getResourceAsStream(name.replace('.', '/') + ".class"));
                        } catch (Exception e4) {
                            e4.addSuppressed(e3);
                            e4.addSuppressed(e2);
                            e4.addSuppressed(e);
                            if (MinestomRootClassLoader.DEBUG) {
                                LoggerFactory.getLogger(SLMixinService.class).warn("Unable to call #getClassNode(): Couldn't load ClassNode for class with name '{}'.", name, e4);
                            }
                            throw new ClassNotFoundException("Could not load ClassNode with name " + name, e4);
                        }
                    }
                }
            }

            reader.accept(node, readerFlags);
            return node;
        }
    };

    @Override
    public void init() {
        SLMixinService.instance = this;
        super.init();
    }

    @Override
    public String getName() {
        return "Starloader Bootstrap";
    }

    @Override
    public boolean isValid() {
        return true;
    }

    @Override
    public IClassProvider getClassProvider() {
        return this.classprovider;
    }

    @Override
    public IClassBytecodeProvider getBytecodeProvider() {
        return this.bytecodeProvider;
    }

    @Override
    public ITransformerProvider getTransformerProvider() {
        return null; // unsupported
    }

    @Override
    public IClassTracker getClassTracker() {
        return null; // unsupported
    }

    @Override
    public IMixinAuditTrail getAuditTrail() {
        return null; // unsupported
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

    public IConsumer<Phase> getPhaseConsumer() {
        return this.wiredPhaseConsumer;
    }

    @Override
    @Deprecated
    public void wire(Phase phase, IConsumer<Phase> phaseConsumer) {
        super.wire(phase, phaseConsumer);
        this.wiredPhaseConsumer = phaseConsumer;
    }

    @Override
    @Deprecated
    public void unwire() {
        super.unwire();
        this.wiredPhaseConsumer = null;
    }

    public static SLMixinService getInstance() {
        return SLMixinService.instance;
    }

    @Nullable
    public final <T extends IMixinInternal> T getMixinInternal(Class<T> type) {
        return this.getInternal(type);
    }
}
