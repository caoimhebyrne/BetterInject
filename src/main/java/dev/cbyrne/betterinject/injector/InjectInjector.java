package dev.cbyrne.betterinject.injector;

import dev.cbyrne.betterinject.annotations.Arg;
import dev.cbyrne.betterinject.annotations.Local;
import dev.cbyrne.betterinject.helpers.CallbackInfoHelper;
import dev.cbyrne.betterinject.utils.CallbackInfoUtils;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.VarInsnNode;
import org.spongepowered.asm.mixin.injection.code.Injector;
import org.spongepowered.asm.mixin.injection.modify.LocalVariableDiscriminator;
import org.spongepowered.asm.mixin.injection.struct.InjectionInfo;
import org.spongepowered.asm.mixin.injection.struct.InjectionNodes.InjectionNode;
import org.spongepowered.asm.mixin.injection.struct.Target;
import org.spongepowered.asm.util.Annotations;

import java.util.Arrays;
import java.util.List;

import static org.spongepowered.asm.mixin.injection.modify.LocalVariableDiscriminator.Context;

public class InjectInjector extends Injector {
    private final boolean isCancellable;
    private final CallbackInfoHelper callbackInfoHelper;

    public InjectInjector(InjectionInfo info, boolean isCancellable) {
        super(info, "@InjectWithArgs");

        this.isCancellable = isCancellable;
        this.callbackInfoHelper = new CallbackInfoHelper(this.isCallbackInfoNeeded());
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

        // CallbackInfo info = new CallbackInfo(...);
        this.callbackInfoHelper.generateCallbackInfo(instructions, target, isCancellable);

        // Load the arguments that are desired from the handler
        this.pushDesiredArguments(instructions, target, injectionNode);

        // Add a method call to the handler to the list
        this.invokeHandler(instructions);

        // Wrap the handler invocation in an if(callbackInfo.isCancelled()) check
        if (isCancellable) {
            this.callbackInfoHelper.wrapInCancellationCheck(instructions, target);
        }

        // Add our instructions before the targeted node
        target.insns.insertBefore(injectionNode.getCurrentTarget(), instructions);
    }

    private void pushDesiredArguments(InsnList instructions, Target target, InjectionNode injectionNode) {
        // Load `this` if not static
        if (!this.isStatic) {
            instructions.add(new VarInsnNode(Opcodes.ALOAD, 0));
        }

        // Let's only push arguments if there's any on the handler method
        if (this.methodArgs.length == 0) {
            return;
        }

        // Convert targetArguments to a List, so we can use `indexOf`
        List<Type> targetArguments = Arrays.asList(target.arguments);
        int amountOfArguments = this.methodArgs.length - (callbackInfoHelper.isCallbackInfoNeeded() ? 1 : 0);

        for (int i = 0; i < amountOfArguments; i++) {
            // We want to get the first argument from the target that matches the current callback argument's type
            Type argumentType = this.methodArgs[i];
            int targetArgumentIndex = targetArguments.indexOf(argumentType);

            // Check if the index was overridden by @Arg or @Local
            AnnotationNode argNode = Annotations.getVisibleParameter(methodNode, Arg.class, i);
            AnnotationNode localNode = Annotations.getVisibleParameter(methodNode, Local.class, i);

            AnnotationNode annotationNode = argNode != null ? argNode : localNode;
            boolean isArgumentNode = argNode != null;

            if (annotationNode != null) {
                this.pushLocalFromAnnotation(
                    instructions,
                    target,
                    injectionNode,
                    annotationNode,
                    argumentType,
                    isArgumentNode
                );
            } else {
                instructions.add(
                    new VarInsnNode(
                        argumentType.getOpcode(Opcodes.ILOAD),
                        target.getArgIndices()[targetArgumentIndex]
                    )
                );
            }
        }

        this.callbackInfoHelper.pushCallbackInfoIfRequired(instructions);
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

    /**
     * Loops over each argument in the handler method, and checks if any of them are CallbackInfo(Returnable).
     */
    private boolean isCallbackInfoNeeded() {
        for (Type argumentType : methodArgs) {
            String desc = argumentType.getDescriptor();
            if (desc.equals(CallbackInfoUtils.DESCRIPTOR) || desc.equals(CallbackInfoUtils.RETURNABLE_DESCRIPTOR)) {
                return true;
            }
        }

        return false;
    }
}
