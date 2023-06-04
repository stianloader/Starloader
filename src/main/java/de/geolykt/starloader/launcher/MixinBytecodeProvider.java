package de.geolykt.starloader.launcher;

import java.io.IOException;
import java.io.InputStream;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.ClassNode;
import org.slf4j.LoggerFactory;

import net.minestom.server.extras.selfmodification.HierarchyClassLoader;
import net.minestom.server.extras.selfmodification.MinestomRootClassLoader;

import de.geolykt.micromixin.BytecodeProvider;
import de.geolykt.micromixin.supertypes.ASMClassWrapperProvider;
import de.geolykt.starloader.util.JavaInterop;

class MixinBytecodeProvider extends ASMClassWrapperProvider implements BytecodeProvider<HierarchyClassLoader> {

    @Override
    @NotNull
    public ClassNode getClassNode(HierarchyClassLoader modularityAttachment, @NotNull String name)
            throws ClassNotFoundException {
        ClassNode node = new ClassNode();
        ClassReader cr;
        try {
            String path = name.replace('.', '/') + ".class";
            InputStream input = modularityAttachment.getResourceAsStreamWithChildren(path);
            if (input == null) {
                throw new ClassNotFoundException("Classloader " + JavaInterop.getClassloaderName(modularityAttachment) + " does not provide " + path);
            }
            cr = new ClassReader(input);
        } catch (IOException e) {
            throw new ClassNotFoundException("Unable to provide classnode", e);
        }
        cr.accept(node, 0);
        return node;
    }

    @Override
    @Nullable
    public ClassNode getNode(@NotNull String name) {
        try {
            return getClassNode(MinestomRootClassLoader.getInstance(), name);
        } catch (ClassNotFoundException cnfe) {
            LoggerFactory.getLogger(getClass()).trace("Cannot resolve node", cnfe);
            return null;
        }
    }
}
