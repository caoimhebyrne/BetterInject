package dev.cbyrne.betterinject.utils;

import org.objectweb.asm.Type;
import org.spongepowered.asm.util.Constants;

public class CallbackInfoReturnableUtils {
    public static String returnFunctionName(Type returnType) {
        if (returnType.getSort() == Type.OBJECT || returnType.getSort() == Type.ARRAY) {
            return "getReturnValue";
        }

        return String.format("getReturnValue%s", returnType.getDescriptor());
    }

    public static String returnFunctionDescriptor(Type returnType) {
        if (returnType.getSort() == Type.OBJECT || returnType.getSort() == Type.ARRAY) {
            return String.format("()%s", Constants.OBJECT_DESC);
        }

        return String.format("()%s", returnType.getDescriptor());
    }
}