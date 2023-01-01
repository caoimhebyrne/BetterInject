package dev.cbyrne.betterinject.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.RUNTIME)
public @interface Arg {
    int ordinal() default -1;

    int index() default -1;

    boolean print() default false;

    String[] names() default {};
}
