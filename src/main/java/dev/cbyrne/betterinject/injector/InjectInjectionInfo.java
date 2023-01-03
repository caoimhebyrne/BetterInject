package dev.cbyrne.betterinject.injector;

import dev.cbyrne.betterinject.annotations.Inject;
import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.MethodNode;
import org.spongepowered.asm.mixin.injection.code.Injector;
import org.spongepowered.asm.mixin.injection.struct.InjectionInfo;
import org.spongepowered.asm.mixin.transformer.MixinTargetContext;
import org.spongepowered.asm.util.Annotations;

@InjectionInfo.AnnotationType(Inject.class)
@InjectionInfo.HandlerPrefix("inject")
public class InjectInjectionInfo extends InjectionInfo{
    public InjectInjectionInfo(MixinTargetContext mixin, MethodNode method, AnnotationNode annotation) {
        super(mixin, method, annotation);
    }

    @Override
    protected Injector parseInjector(AnnotationNode injectAnnotation) {
        boolean isCancellable = Annotations.getValue(injectAnnotation, "cancellable", Boolean.FALSE);
        boolean print = Annotations.getValue(injectAnnotation, "print", Boolean.FALSE);

        return new InjectInjector(this, isCancellable, print);
    }
}
