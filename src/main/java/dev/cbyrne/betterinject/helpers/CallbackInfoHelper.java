package dev.cbyrne.betterinject.helpers;

import dev.cbyrne.betterinject.utils.CallbackInfoUtils;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.struct.Target;
import org.spongepowered.asm.util.Constants;

/**
 * A helper class for generating instructions for CallbackInfo.
 */
public class CallbackInfoHelper {
    /**
     * The local index for the CallbackInfo owned by this class
     */
    private int callbackInfoIndex = -1;

    /**
     * If CallbackInfo is needed for this Target or not.
     */
    private final boolean isCallbackInfoNeeded;

    /**
     * If the next instruction is a return instruction or not
     */
    private final boolean nextInsnIsReturn;

    public CallbackInfoHelper(boolean isCallbackInfoNeeded, boolean nextInsnIsReturn) {
        this.isCallbackInfoNeeded = isCallbackInfoNeeded;
        this.nextInsnIsReturn = nextInsnIsReturn;
    }

    /**
     * If the CallbackInfo is needed or not
     */
    public boolean isCallbackInfoNeeded() {
        return this.isCallbackInfoNeeded;
    }

    /**
     * If the callback info was generated
     *
     * @see #generateCallbackInfo(InsnList, Target, boolean)
     */
    public boolean didGenerateCallbackInfo() {
        return this.callbackInfoIndex != -1;
    }

    /**
     * Generates instructions for instantiating a new CallbackInfo.
     *
     * <pre>CallbackInfo ci = new CallbackInfo("target.method.name", isMethodCancellable);</pre>
     */
    public void generateCallbackInfo(InsnList instructions, Target target, boolean isMethodCancellable) {
        if (!this.isCallbackInfoNeeded() || this.didGenerateCallbackInfo()) {
            return;
        }

        this.callbackInfoIndex = target.allocateLocal();
        String callbackInfoClass = CallbackInfo.getCallInfoClassName(target.returnType);
        String callbackInfoCtorDesc = CallbackInfoUtils.CTOR;

        // We need to store the return type
        int returnTypeLocal = target.allocateLocal();
        if (this.nextInsnIsReturn && !target.returnType.equals(Type.VOID_TYPE)) {
            int dupCode = target.returnType.getSize() == 1 ? Opcodes.DUP : Opcodes.DUP2;
            instructions.add(new InsnNode(dupCode));
            instructions.add(new VarInsnNode(target.returnType.getOpcode(Opcodes.ISTORE), returnTypeLocal));
        }

        // new CallbackInfo
        instructions.add(new TypeInsnNode(Opcodes.NEW, callbackInfoClass));
        instructions.add(new InsnNode(Opcodes.DUP));
        // "{target.method.name}"
        instructions.add(new LdcInsnNode(target.method.name));
        // isCancellable
        instructions.add(new InsnNode(isMethodCancellable ? Opcodes.ICONST_1 : Opcodes.ICONST_0));
        if (this.nextInsnIsReturn && !target.returnType.equals(Type.VOID_TYPE)) {
            // We need to load a local of the target's return type to pass the return type to the CallbackInfoReturnable ctor
            instructions.add(new VarInsnNode(target.returnType.getOpcode(Opcodes.ILOAD), returnTypeLocal));
            callbackInfoCtorDesc = CallbackInfoUtils.constructorDescriptor(target.returnType);
        }
        // () <- new CallbackInfo("{target.method.name}", isCancellable, ...)
        instructions.add(
            new MethodInsnNode(
                Opcodes.INVOKESPECIAL,
                callbackInfoClass,
                Constants.CTOR,
                callbackInfoCtorDesc,
                false
            )
        );

        // index: callbackInfo(index)
        target.addLocalVariable(
            this.callbackInfoIndex,
            "callbackInfo" + this.callbackInfoIndex,
            "L" + callbackInfoClass + ";"
        );

        // Store new CallbackInfo(...) in the allocated index
        instructions.add(new VarInsnNode(Opcodes.ASTORE, this.callbackInfoIndex));
    }

    /**
     * Adds "ALOAD {callbackInfoIndex}" to the instruction list
     */
    public void pushCallbackInfoIfRequired(InsnList instructions) {
        if (!this.isCallbackInfoNeeded() || !this.didGenerateCallbackInfo()) return;

        instructions.add(new VarInsnNode(Opcodes.ALOAD, this.callbackInfoIndex));
    }

    /**
     * Inserts a isCancelled() check on to the stack, if the method was cancelled, it returns.
     * <p/>
     * <pre>
     * if (!callbackInfo.isCancelled) {
     *     ...
     * } else {
     *     return;
     *
     *     // or
     *
     *     T value = callbackInfo.getReturnValue();
     *     return value;
     * }
     * </pre>
     */
    public void wrapInCancellationCheck(InsnList instructions, Target target) {
        if (!this.isCallbackInfoNeeded || !this.didGenerateCallbackInfo()) return;

        // Get the class name (CallbackInfo or CallbackInfoReturnable)
        String callbackInfoClass = CallbackInfo.getCallInfoClassName(target.returnType);

        // Load our instance of callback info
        instructions.add(new VarInsnNode(Opcodes.ALOAD, callbackInfoIndex));

        // callbackInfo.isCancelled()
        instructions.add(new MethodInsnNode(
            Opcodes.INVOKEVIRTUAL,
            callbackInfoClass,
            "isCancelled",
            "()Z",
            false)
        );

        // if (!callbackInfo.isCancelled) {
        //     ...
        // }
        LabelNode ifNotCancelled = new LabelNode();
        instructions.add(new JumpInsnNode(Opcodes.IFEQ, ifNotCancelled));

        if (target.returnType == Type.VOID_TYPE) {
            // return;
            instructions.add(new InsnNode(Opcodes.RETURN));
        } else {
            instructions.add(new VarInsnNode(Opcodes.ALOAD, this.callbackInfoIndex));

            // CallbackInfoReturnable.getReturnValue{X}()
            instructions.add(new MethodInsnNode(
                Opcodes.INVOKEVIRTUAL,
                callbackInfoClass,
                CallbackInfoUtils.returnFunctionName(target.returnType),
                CallbackInfoUtils.returnFunctionDescriptor(target.returnType),
                false
            ));

            // If the return type is an object, method, etc.
            if (target.returnType.getSort() >= Type.ARRAY) {
                // We need to cast the Object to the return type
                instructions.add(new TypeInsnNode(Opcodes.CHECKCAST, target.returnType.getInternalName()));
            }

            // return
            instructions.add(new InsnNode(target.returnType.getOpcode(Opcodes.IRETURN)));
        }

        instructions.add(ifNotCancelled);
    }
}
