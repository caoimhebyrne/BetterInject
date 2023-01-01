package dev.cbyrne.betterinject.utils;

import org.objectweb.asm.Type;
import org.objectweb.asm.tree.MethodNode;
import org.spongepowered.asm.util.Annotations;

import java.lang.annotation.Annotation;
import java.util.List;

public class AnnotationUtils {
    public static boolean allArgumentsAreAnnotatedWith(
        MethodNode node,
        Type[] methodArgs,
        Class<? extends Annotation> clazz,
        List<Class<? extends Annotation>> exceptions
    ) {
        for (int i = 0; i < methodArgs.length; i++) {
            Type argumentType = methodArgs[i];

            // We want to ignore CallbackInfo
            if (CallbackInfoUtils.typeIsCallbackInfo(argumentType)) {
                continue;
            }

            // We also want to ignore if it's annotated with any of the exceptions
            boolean annotatedWithException = false;
            for (Class<? extends Annotation> exception : exceptions) {
                if (Annotations.getVisibleParameter(node, exception, i) != null) {
                    annotatedWithException = true;
                    break; // We found an exception, meaning we can go on to the next parameter
                }
            }

            // The method was annotated with one of the exceptions, we don't want to check it.
            if (annotatedWithException) {
                continue;
            }

            // If this parameter is not annotated with the `clazz`, the check has failed
            if (Annotations.getVisibleParameter(node, clazz, i) == null) {
                return false;
            }
        }

        // All arguments are annotated with `clazz`
        return true;
    }
}
