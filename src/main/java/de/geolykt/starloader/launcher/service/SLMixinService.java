package de.geolykt.starloader.launcher.service;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.Arrays;
import java.util.Collection;

import org.jetbrains.annotations.Nullable;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.ClassNode;
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

public class SLMixinService extends MixinServiceAbstract {

    private static SLMixinService instance;

    private static final MinestomRootClassLoader CLASSLOADER = MinestomRootClassLoader.getInstance();
    private IConsumer<Phase> wiredPhaseConsumer;

    private final IClassProvider classprovider = new IClassProvider() {

        @Override
        public URL[] getClassPath() {
            return CLASSLOADER.getURLs();
        }

        @Override
        public Class<?> findClass(String name, boolean initialize) throws ClassNotFoundException {
            return Class.forName(name, initialize, Thread.currentThread().getContextClassLoader());
        }

        @Override
        public Class<?> findAgentClass(String name, boolean initialize) throws ClassNotFoundException {
            return Class.forName(name, initialize, CLASSLOADER);
        }

        @Override
        public Class<?> findClass(String name) throws ClassNotFoundException {
            return CLASSLOADER.findClass(name);
        }
    };

    // TODO documentation of that interface is nonsensical, complain
    private final IClassBytecodeProvider bytecodeProvider = new IClassBytecodeProvider() {
        @Override
        public ClassNode getClassNode(String name, boolean runTransformers) throws ClassNotFoundException, IOException {
            ClassNode node = new ClassNode();
            ClassReader reader;
            try {
                reader = new ClassReader(CLASSLOADER.loadBytesWithChildren(name, runTransformers));
            } catch (Throwable e) {
                throw new ClassNotFoundException("Could not load ClassNode with name " + name, e);
            }
            reader.accept(node, 0);
            return node;
        }

        @Override
        public ClassNode getClassNode(String name) throws ClassNotFoundException, IOException {
            return getClassNode(name, false);
        }
    };

    @Override
    public void init() {
        instance = this;
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
        return classprovider;
    }

    @Override
    public IClassBytecodeProvider getBytecodeProvider() {
        return bytecodeProvider;
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
        return new ContainerHandleVirtual(getName());
    }

    @Override
    public InputStream getResourceAsStream(String name) {
        return CLASSLOADER.getResourceAsStreamWithChildren(name);
    }

    public IConsumer<Phase> getPhaseConsumer() {
        return wiredPhaseConsumer;
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
        return instance;
    }

    @Nullable
    public final <T extends IMixinInternal> T getMixinInternal(Class<T> type) {
        return getInternal(type);
    }
}
