package dev.cbyrne.betterinject.utils;

import org.objectweb.asm.Type;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.spongepowered.asm.util.Constants;

public class CallbackInfoUtils {
    public static final String DESCRIPTOR = String.format("L%s;", CallbackInfo.class.getName().replace(".", "/"));
    public static final String RETURNABLE_DESCRIPTOR = String.format("L%s;", CallbackInfoReturnable.class.getName().replace(".", "/"));

    public static boolean typeIsCallbackInfo(Type type) {
        String desc = type.getDescriptor();
        return desc.equals(DESCRIPTOR) || desc.equals(RETURNABLE_DESCRIPTOR);
    }

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