package dev.cbyrne.betterinject.injector.strategy;

import dev.cbyrne.betterinject.annotations.Arg;
import dev.cbyrne.betterinject.annotations.Local;
import dev.cbyrne.betterinject.utils.AnnotationUtils;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.MethodNode;

import java.lang.annotation.Annotation;
import java.util.ArrayList;
import java.util.List;

/**
 * How arguments should be handled, either {@link ArgumentHandlingStrategy#STRICT} or {@link ArgumentHandlingStrategy#LIGHT}.
 */
public enum ArgumentHandlingStrategy {
    /**
     * No parameters on the handler function are annotated with @Arg.
     * This means that we are allowed to throw an error they don't all exist.
     */
    STRICT,

    /**
     * At least one of the parameters on the handler function have an @Arg annotation.
     * This means we can be more lenient about whether they have all the arguments or not.
     */
    LIGHT;

    private static final List<Class<? extends Annotation>> EXCEPTIONS = new ArrayList<>();

    static {
        EXCEPTIONS.add(Local.class);
    }

    public static ArgumentHandlingStrategy fromMethod(MethodNode node, Type[] methodArgs) {
        if (methodArgs == null || methodArgs.length == 0) {
            return ArgumentHandlingStrategy.LIGHT;
        }

        boolean allAreAnnotatedWithArg = AnnotationUtils.allArgumentsAreAnnotatedWith(node, methodArgs, Arg.class, EXCEPTIONS);
        return allAreAnnotatedWithArg ? LIGHT : STRICT;
    }
}
