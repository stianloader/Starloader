package org.stianloader.sll.impl.classtransform;

import java.lang.instrument.Instrumentation;
import java.security.ProtectionDomain;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Supplier;

import org.jetbrains.annotations.NotNull;

import net.lenni0451.classtransform.TransformerManager;
import net.lenni0451.classtransform.annotations.injection.CASM;
import net.lenni0451.classtransform.transformer.AnnotationHandler;
import net.lenni0451.classtransform.transformer.HandlerPosition;
import net.lenni0451.classtransform.transformer.IAnnotationHandlerPreprocessor;
import net.lenni0451.classtransform.transformer.IBytecodeTransformer;
import net.lenni0451.classtransform.transformer.IPostTransformer;
import net.lenni0451.classtransform.transformer.IRawTransformer;
import net.lenni0451.classtransform.transformer.impl.CASMAnnotationHandler;
import net.lenni0451.classtransform.transformer.impl.CInjectAnnotationHandler;
import net.lenni0451.classtransform.transformer.impl.CInlineAnnotationHandler;
import net.lenni0451.classtransform.transformer.impl.CModifyConstantAnnotationHandler;
import net.lenni0451.classtransform.transformer.impl.CModifyExpressionValueAnnotationHandler;
import net.lenni0451.classtransform.transformer.impl.COverrideAnnotationHandler;
import net.lenni0451.classtransform.transformer.impl.CRecordComponentAnnotationHandler;
import net.lenni0451.classtransform.transformer.impl.CRedirectAnnotationHandler;
import net.lenni0451.classtransform.transformer.impl.CReplaceCallbackAnnotationHandler;
import net.lenni0451.classtransform.transformer.impl.CShadowAnnotationHandler;
import net.lenni0451.classtransform.transformer.impl.CUpgradeAnnotationHandler;
import net.lenni0451.classtransform.transformer.impl.CWrapCatchAnnotationHandler;
import net.lenni0451.classtransform.transformer.impl.CWrapConditionAnnotationHandler;
import net.lenni0451.classtransform.transformer.impl.general.InnerClassGeneralHandler;
import net.lenni0451.classtransform.transformer.impl.general.MemberCopyGeneralHandler;
import net.lenni0451.classtransform.transformer.impl.general.SyntheticMethodGeneralHandler;
import net.lenni0451.classtransform.utils.tree.ClassTree;
import net.lenni0451.classtransform.utils.tree.IClassProvider;

final class NOPTransformManager extends TransformerManager {
    private static final class SLLCTNoClassProvider implements IClassProvider {
        @Override
        public Map<String, Supplier<byte[]>> getAllClasses() {
            throw new UnsupportedOperationException("This method should never be called in normal operation.");
        }

        @Override
        public byte[] getClass(String name) throws ClassNotFoundException {
            throw new UnsupportedOperationException("This method should never be called in normal operation.");
        }
    }

    @NotNull
    private final List<AnnotationHandler> annotationHandler = new CopyOnWriteArrayList<>();

    public NOPTransformManager() {
        super(new SLLCTNoClassProvider());

        // What can go wrong lol

        this.annotationHandler.add(new CASMAnnotationHandler(CASM.Shift.TOP));
        this.annotationHandler.add(new InnerClassGeneralHandler()); //Make inner classes public to allow access from the transformed class
        this.annotationHandler.add(new SyntheticMethodGeneralHandler()); //Rename synthetic members to be unique
        this.annotationHandler.add(new CShadowAnnotationHandler());
        this.annotationHandler.add(new CRecordComponentAnnotationHandler());
        this.annotationHandler.add(new MemberCopyGeneralHandler(true)); //Copy all interfaces, fields and initializers to the transformed class
        //HandlerPosition#PRE
        this.annotationHandler.add(new COverrideAnnotationHandler());
        this.annotationHandler.add(new CWrapCatchAnnotationHandler());
        this.annotationHandler.add(new CInjectAnnotationHandler());
        this.annotationHandler.add(new CModifyExpressionValueAnnotationHandler());
        this.annotationHandler.add(new CModifyConstantAnnotationHandler());
        this.annotationHandler.add(new CWrapConditionAnnotationHandler());
        this.annotationHandler.add(new CRedirectAnnotationHandler()); //Should be last because it replaces the method node which prevents other handlers from targeting the method
        //HandlerPosition#POST
        this.annotationHandler.add(new CUpgradeAnnotationHandler());
        this.annotationHandler.add(new MemberCopyGeneralHandler(false)); //Copy all leftover methods to the transformed class
        this.annotationHandler.add(new CInlineAnnotationHandler());
        this.annotationHandler.add(new CReplaceCallbackAnnotationHandler());
        this.annotationHandler.add(new CASMAnnotationHandler(CASM.Shift.BOTTOM));
    }

    @Override
    public void addBytecodeTransformer(IBytecodeTransformer bytecodeTransformer) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void addPostTransformConsumer(IPostTransformer postTransformer) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void addRawTransformer(String className, IRawTransformer rawTransformer) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void addTransformerPreprocessor(IAnnotationHandlerPreprocessor annotationHandlerPreprocessor) {
        throw new UnsupportedOperationException();
    }

    @Override
    public ClassTree getClassTree() {
        return new ClassTree(null);
    }

    @Override
    public Instrumentation getInstrumentation() {
        throw new UnsupportedOperationException();
    }

    @Override
    public Set<String> getRegisteredTransformer() {
        throw new UnsupportedOperationException();
    }

    @Override
    public Set<String> getTransformedClasses() {
        throw new UnsupportedOperationException();
    }

    @Override
    public byte[] transform(ClassLoader loader, String className, Class<?> classBeingRedefined,
            ProtectionDomain protectionDomain, byte[] classfileBuffer) {
        throw new UnsupportedOperationException();
    }

    @Override
    public byte[] transform(String name, byte[] bytecode, boolean calculateStackMapFrames) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void addCustomAnnotationHandler(final AnnotationHandler transformer, final HandlerPosition handlerPosition) {
        handlerPosition.add(this.annotationHandler, transformer);
    }

    @NotNull
    public List<AnnotationHandler> getAnnotationHandlerList() {
        return this.annotationHandler;
    }
}
