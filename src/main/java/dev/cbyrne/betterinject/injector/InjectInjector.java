package dev.cbyrne.betterinject.injector;

import dev.cbyrne.betterinject.annotations.Arg;
import dev.cbyrne.betterinject.annotations.Local;
import dev.cbyrne.betterinject.utils.CallbackInfoReturnableUtils;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.spongepowered.asm.mixin.injection.code.Injector;
import org.spongepowered.asm.mixin.injection.modify.LocalVariableDiscriminator;
import org.spongepowered.asm.mixin.injection.struct.InjectionInfo;
import org.spongepowered.asm.mixin.injection.struct.InjectionNodes.InjectionNode;
import org.spongepowered.asm.mixin.injection.struct.Target;
import org.spongepowered.asm.util.Annotations;
import org.spongepowered.asm.util.Constants;

import java.util.Arrays;
import java.util.List;

import static org.spongepowered.asm.mixin.injection.modify.LocalVariableDiscriminator.Context;

public class InjectInjector extends Injector {
    private final boolean isCancellable;
    private final boolean shouldGenerateCallbackInfo;
    private final List<Type> callbackArguments;

    public InjectInjector(InjectionInfo info, boolean isCancellable) {
        super(info, "@InjectWithArgs");

        this.isCancellable = isCancellable;
        this.callbackArguments = Arrays.asList(methodArgs);
        this.shouldGenerateCallbackInfo =
            this.callbackArguments.contains(Type.getType(CallbackInfo.class)) ||
            this.callbackArguments.contains(Type.getType(CallbackInfoReturnable.class));
    }

    /**
     * @param target Everything about the target class
     * @param node   The node which we are injecting to, i.e. the first instruction if @At("HEAD")
     */
    @Override
    protected void inject(Target target, InjectionNode node) {
        this.checkTargetModifiers(target, true);
        this.injectInvokeCallback(target, node);
    }

    private void injectInvokeCallback(Target target, InjectionNode injectionNode) {
        InsnList instructions = new InsnList();

        int callbackInfoIndex = this.generateCallbackInfo(instructions, target);

        // Load the arguments that are desired from the handler
        this.pushDesiredArguments(instructions, target, injectionNode, callbackInfoIndex);

        // Add a method call to the handler to the list
        this.invokeHandler(instructions);

        // Wrap the handler invocation in an if(callbackInfo.isCancelled()) check
        if (isCancellable) {
            this.wrapInCancellationCheck(instructions, target, callbackInfoIndex);
        }

        // Add our instructions before the targeted node
        target.insns.insertBefore(injectionNode.getCurrentTarget(), instructions);
    }

    private void pushDesiredArguments(
        InsnList instructions,
        Target target,
        InjectionNode injectionNode,
        int callbackInfoIndex
    ) {
        // Load `this` if not static
        if (!this.isStatic) {
            instructions.add(new VarInsnNode(Opcodes.ALOAD, 0));
        }

        // Let's only push arguments if there's any on the handler method
        if (this.callbackArguments.size() == 0) {
            return;
        }

        // Convert targetArguments to a List, so we can use `indexOf`
        List<Type> targetArguments = Arrays.asList(target.arguments);
        int amountOfArguments = this.callbackArguments.size() - (this.shouldGenerateCallbackInfo ? 1 : 0);

        for (int i = 0; i < amountOfArguments; i++) {
            // We want to get the first argument from the target that matches the current callback argument's type
            Type argument = this.callbackArguments.get(i);
            int targetArgumentIndex = targetArguments.indexOf(argument);

            // Check if the index was overridden by @Arg or @Local
            AnnotationNode argNode = Annotations.getVisibleParameter(methodNode, Arg.class, i);
            AnnotationNode localNode = Annotations.getVisibleParameter(methodNode, Local.class, i);

            AnnotationNode annotationNode = argNode != null ? argNode : localNode;
            boolean isArgumentNode = argNode != null;

            if (annotationNode != null) {
                this.pushLocalFromAnnotation(instructions, target, injectionNode, annotationNode, argument, isArgumentNode);
            } else {
                instructions.add(new VarInsnNode(argument.getOpcode(Opcodes.ILOAD), target.getArgIndices()[targetArgumentIndex]));
            }
        }

        if (this.shouldGenerateCallbackInfo) {
            instructions.add(new VarInsnNode(Opcodes.ALOAD, callbackInfoIndex));
        }
    }

    /**
     * Pushes a local variable on to the call stack from a {@link Arg} or {@link Local}.
     */
    private void pushLocalFromAnnotation(
        InsnList instructions,
        Target target,
        InjectionNode injectionNode,
        AnnotationNode annotationNode,
        Type desiredType,
        boolean argumentsOnly
    ) {
        LocalVariableDiscriminator discriminator = LocalVariableDiscriminator.parse(annotationNode);
        Context context = new Context(
            this.info,
            desiredType,
            argumentsOnly,
            target,
            injectionNode.getCurrentTarget()
        );

        int local = discriminator.findLocal(context);
        instructions.add(new VarInsnNode(desiredType.getOpcode(Opcodes.ILOAD), local));
    }

    private int generateCallbackInfo(InsnList instructions, Target target) {
        if (!this.shouldGenerateCallbackInfo) {
            return -1;
        }

        int index = target.allocateLocal();
        String callbackInfoClass = CallbackInfo.getCallInfoClassName(target.returnType);

        // new CallbackInfo
        instructions.add(new TypeInsnNode(Opcodes.NEW, callbackInfoClass));
        instructions.add(new InsnNode(Opcodes.DUP));
        // "{target.method.name}"
        instructions.add(new LdcInsnNode(target.method.name));
        // isCancellable
        instructions.add(new InsnNode(isCancellable ? Opcodes.ICONST_1 : Opcodes.ICONST_0));
        // () <- new CallbackInfo("{target.method.name}", isCancellable)
        instructions.add(
            new MethodInsnNode(
                Opcodes.INVOKESPECIAL,
                callbackInfoClass,
                Constants.CTOR,
                "(Ljava/lang/String;Z)V",
                false
            )
        );

        // index: callbackInfo(index)
        target.addLocalVariable(index, "callbackInfo" + index, "L" + callbackInfoClass + ";");

        // Store new CallbackInfo(...) in the allocated index
        instructions.add(new VarInsnNode(Opcodes.ASTORE, index));
        return index;
    }

    private void wrapInCancellationCheck(InsnList instructions, Target target, int callbackInfoIndex) {
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
            instructions.add(new VarInsnNode(Opcodes.ALOAD, callbackInfoIndex));

            // CallbackInfoReturnable.getReturnValue{X}()
            instructions.add(new MethodInsnNode(
                Opcodes.INVOKEVIRTUAL,
                callbackInfoClass,
                CallbackInfoReturnableUtils.returnFunctionName(target.returnType),
                CallbackInfoReturnableUtils.returnFunctionDescriptor(target.returnType),
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
