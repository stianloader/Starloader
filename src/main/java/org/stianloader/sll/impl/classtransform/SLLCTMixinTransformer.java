package org.stianloader.sll.impl.classtransform;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.net.URI;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.json.JSONArray;
import org.json.JSONObject;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.ClassNode;
import org.slf4j.LoggerFactory;
import org.stianloader.sll.transform.CodeTransformer;

import net.lenni0451.classtransform.mixinstranslator.MixinsTranslator;
import net.lenni0451.classtransform.transformer.AnnotationHandler;
import net.minestom.server.extras.selfmodification.MinestomRootClassLoader;

import de.geolykt.starloader.transformers.ASMTransformer;
import de.geolykt.starloader.util.JavaInterop;

public class SLLCTMixinTransformer extends ASMTransformer implements CodeTransformer {

    @NotNull
    private final Map<String, Collection<ClassNode>> ctTransformers = new ConcurrentHashMap<>();
    @NotNull
    private final NOPTransformManager dummyCtManager = new NOPTransformManager();

    @Override
    public boolean accept(@NotNull ClassNode source) {
        if (MinestomRootClassLoader.getInstance().isThreadLoggingClassloadingFailures()) {
            try {
                throw new RuntimeException("Stacktrace");
            } catch (RuntimeException e) {
                LoggerFactory.getLogger(SLLCTMixinTransformer.class).warn("SLLMixinTransformer implements CodeTransformer, meaning that CodeTransformer#transformClass should be called instead of ASMTransformer#accept. Please report this issue to the caller. Note: This is not a fatal issue, but should be handled in due time.", e);
            }
        }

        return this.transformClass(source, null);
    }

    public synchronized void addCTTransformer(@NotNull ClassNode node, boolean requireAnnotation) {
        Collection<AnnotationNode> annotationsA = node.visibleAnnotations;
        Collection<AnnotationNode> annotationsB = node.invisibleAnnotations;

        if (annotationsA == null) {
            annotationsA = Collections.emptyList();
        }

        if (annotationsB == null) {
            annotationsB = Collections.emptyList();
        }

        Collection<AnnotationNode> allAnnotations = new ArrayList<>();
        allAnnotations.addAll(annotationsA);
        allAnnotations.addAll(annotationsB);
        AnnotationNode ctTransformerAnnotation = null;

        for (AnnotationNode annotation : allAnnotations) {
            if (annotation.desc.equals("Lnet/lenni0451/classtransform/annotations/CTransformer;")) {
                if (ctTransformerAnnotation != null) {
                    LoggerFactory.getLogger(SLLCTMixinTransformer.class).warn("Duplicate @CTransformer annotation for node '{}', despite the annotation not being marked as @Repeatable! Ignoring duplicate...", node.name);
                    continue;
                }
                ctTransformerAnnotation = annotation;
            }
        }

        if (ctTransformerAnnotation == null) {
            if (requireAnnotation) {
                throw new IllegalStateException("The CT transformer node " + node.name + " has no @CTransformer annotation, despite it being required. Did you accidentally pass in a Mixin class as-is?");
            } else {
                return;
            }
        }

        List<Object> annotationValues = ctTransformerAnnotation.values;

        if (annotationValues == null) {
            LoggerFactory.getLogger(SLLCTMixinTransformer.class).warn("@CTransformer annotation for node '{}' has no values. This implies that the given transformer has no effects - it's probably not intended behaviour.");
            return;
        }

        for (int i = 0; i < annotationValues.size(); i += 2) {
            String key = (String) annotationValues.get(i);
            Object value = annotationValues.get(i + 1);

            if (!key.equals("name") && !key.equals("value")) {
                LoggerFactory.getLogger(SLLCTMixinTransformer.class).warn("Unknown value name '{}' for @CTransformer annotation present in transformation node '{}'. Skipping value, but this could have adverse effects!", key, node.name);
                continue;
            }

            if (!(value instanceof List<?>)) {
                String valueType = "null";
                if (value != null) {
                    valueType = value.getClass().getName();
                }
                throw new IllegalStateException("Broken/malformed @CTransformer annotation value for key '" + key + "' in node '" + node.name + "': Expected an instance of java/util/List, but instead got '" + value + "' of type '" + valueType + "'. This could be a compiler error, or induced by an inccorectly coded class generator, regardless it is outright incorrect.");
            }

            for (Object v : (List<?>) value) {
                String className;

                if (v instanceof String) {
                    className = ((String) v).replace('.', '/');
                } else if (v instanceof Type) {
                    className = ((Type) v).getInternalName();
                } else {
                    throw new IllegalStateException("Unknown type '" + (v == null ? "null" : v.getClass().getName()) + "'. Affected node: " + node.name);
                }

                this.ctTransformers.compute(className, (ignored, transformers) -> {
                    if (transformers == null) {
                        transformers = ConcurrentHashMap.newKeySet();
                    }
                    transformers.add(node);
                    return transformers;
                });
            }
        }
    }

    public void addMixins(@NotNull URLClassLoader loader, @NotNull JSONObject mixinConfigJson) {
        String mixinPackage = mixinConfigJson.optString("package");
        if (mixinPackage == null) {
            throw new IllegalStateException("The required field \"package\" is missing from the mixin configuration.");
        }

        mixinPackage = mixinPackage.replace('.', '/') + (mixinPackage.isEmpty() ? "" : "/");

        Set<String> mixinList = new HashSet<>();
        JSONArray mixins = mixinConfigJson.optJSONArray("mixins");
        JSONArray client = mixinConfigJson.optJSONArray("client");
        JSONArray server = mixinConfigJson.optJSONArray("server");
        if (mixins != null) {
            for (Object o : mixins) {
                mixinList.add(o.toString().replace('.', '/'));
            }
        }

        if (client != null) {
            for (Object o : client) {
                mixinList.add(o.toString().replace('.', '/'));
            }
        }
        if (server != null) {
            for (Object o : server) {
                mixinList.add(o.toString().replace('.', '/'));
            }
        }

        // FIXME handle mixin priorities

        for (String mixin : mixinList) {
            try (InputStream is = loader.getResourceAsStream(mixinPackage + mixin.replace('.', '/') + ".class")) {
                if (is == null) {
                    throw new IllegalStateException("Cannot find mixin " + mixin + " in classloader " + JavaInterop.getClassloaderName(loader));
                }

                ClassNode node = new ClassNode();
                ClassReader reader = new ClassReader(is);
                reader.accept(node, 0);

                MixinsTranslator translator = new MixinsTranslator();
                translator.process(node);

                this.addCTTransformer(node, true);
            } catch (IOException e) {
                throw new UncheckedIOException("Cannot fetch mixin " + mixin + " in classlaoder " + JavaInterop.getClassloaderName(loader), e);
            }
        }
    }

    @Override
    public int getPriority() {
        return -10_000;
    }

    @Override
    public boolean isValidTarget(@NotNull String internalName) {
        if (MinestomRootClassLoader.getInstance().isThreadLoggingClassloadingFailures()) {
            try {
                throw new RuntimeException("Stacktrace");
            } catch (RuntimeException e) {
                LoggerFactory.getLogger(SLLCTMixinTransformer.class).warn("SLLMixinTransformer implements CodeTransformer, meaning that CodeTransformer#isValidTarget(String, URI) should be called instead of ASMTransformer#isValidTarget(String). Please report this issue to the caller. Note: This is not a fatal issue, but should be handled in due time.", e);
            }
        }

        return this.isValidTarget(internalName, null);
    }

    @Override
    public boolean isValidTarget(@NotNull String internalName, @Nullable URI codeSourceURI) {
        return this.ctTransformers.containsKey(internalName);
    }

    @Override
    public boolean transformClass(@NotNull ClassNode node, @Nullable URI codeSourceURI) {
        Collection<ClassNode> transformers = this.ctTransformers.get(node.name);
        if (transformers == null) {
            return false;
        }

        for (ClassNode transformer : transformers) {
            for (AnnotationHandler annotationHandler : this.dummyCtManager.getAnnotationHandlerList()) {
                try {
                    annotationHandler.transform(this.dummyCtManager, node, transformer);
                } catch (Exception e) {
                    throw new RuntimeException("Failed to transform class: '" + node.name + "' whilst applying CT transformer '" + transformer.name + "'", e);
                }
            }
        }

        return true;
    }
}
