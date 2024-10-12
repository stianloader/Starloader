package de.geolykt.starloader.launcher;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.Map;
import java.util.Objects;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.ClassNode;
import org.slf4j.LoggerFactory;
import org.stianloader.micromixin.transform.api.BytecodeProvider;
import org.stianloader.micromixin.transform.api.CodeSourceURIProvider;
import org.stianloader.micromixin.transform.api.supertypes.ASMClassWrapperProvider;

import net.minestom.server.extras.selfmodification.HierarchyClassLoader;
import net.minestom.server.extras.selfmodification.MinestomRootClassLoader;

import de.geolykt.starloader.util.JavaInterop;

final class MixinBytecodeProvider extends ASMClassWrapperProvider implements BytecodeProvider<HierarchyClassLoader>, CodeSourceURIProvider<HierarchyClassLoader> {

    @NotNull
    private final Map<String, String> smapURIAliases;

    public MixinBytecodeProvider(@NotNull Map<String, String> smapURIAliases) {
        this.smapURIAliases = smapURIAliases;
    }

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
            return this.getClassNode(MinestomRootClassLoader.getInstance(), name);
        } catch (ClassNotFoundException cnfe) {
            LoggerFactory.getLogger(MixinBytecodeProvider.class).trace("Cannot resolve node", cnfe);
            return null;
        }
    }

    @Override
    @Nullable
    public URI findURI(@Nullable HierarchyClassLoader modularityAttachment, @NotNull String internalClassName) {
        URL url = Objects.requireNonNull(modularityAttachment).getResourceAsURLWithChildren(internalClassName.replace('.', '/') + ".class");
        URI uri = Utils.toCodeSourceURI(url, internalClassName);
        if (uri != null) {
            try {
                String uriString = uri.toString();
                uriString = this.smapURIAliases.getOrDefault(uriString, uriString);
                return new URI(uriString);
            } catch (URISyntaxException e) {
                LoggerFactory.getLogger(MixinBytecodeProvider.class).warn("Cannot convert url to URI: {}", url, e);
                return null;
            }
        } else {
            return null;
        }
    }
}
