package dev.cbyrne.betterinject.injector;

import dev.cbyrne.betterinject.annotations.Arg;
import dev.cbyrne.betterinject.annotations.Local;
import dev.cbyrne.betterinject.helpers.CallbackInfoHelper;
import dev.cbyrne.betterinject.injector.strategy.ArgumentHandlingStrategy;
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
import org.spongepowered.asm.mixin.injection.throwables.InjectionError;
import org.spongepowered.asm.util.Annotations;

import java.util.Arrays;
import java.util.List;

import static org.spongepowered.asm.mixin.injection.modify.LocalVariableDiscriminator.Context;

public class InjectInjector extends Injector {
    private final boolean isCancellable;
    private final ArgumentHandlingStrategy argumentStrategy;

    public InjectInjector(InjectionInfo info, boolean isCancellable) {
        super(info, "@InjectWithArgs");

        this.isCancellable = isCancellable;
        this.argumentStrategy = ArgumentHandlingStrategy.fromMethod(this.methodNode, this.methodArgs);
    }

    /**
     * @param target Everything about the target class
     * @param node   The node which we are injecting to, i.e. the first instruction if @At("HEAD")
     */
    @Override
    protected void inject(Target target, InjectionNode node) {
        CallbackInfoHelper callbackInfoHelper = new CallbackInfoHelper(this.isCallbackInfoNeeded());
        node.decorate("callbackInfoHelper", callbackInfoHelper);

        if (argumentStrategy == ArgumentHandlingStrategy.STRICT) {
            // We are on strict mode, let's check if all the arguments from the target are present on the callback.
            this.checkArgumentsStrict(target);
        }

        this.checkTargetModifiers(target, true);
        this.injectInvokeCallback(target, node);
    }

    /**
     * Ensures that all the arguments from the target are on the handler, in the same order.
     * This is called when the Injector is in {@link ArgumentHandlingStrategy#STRICT}.
     *
     * @see #inject(Target, InjectionNode)
     */
    private void checkArgumentsStrict(Target target) {
        // There are no arguments to check
        if (target.arguments.length == 0) {
            return;
        }

        // The target has more than the handler
        if (target.arguments.length > methodArgs.length) {
            strictModeArgumentCheckError(target);
        }

        for (int i = 0; i < target.arguments.length; i++) {
            Type targetArgument = target.arguments[i];
            Type handlerArgument = methodArgs[i];

            if (!targetArgument.equals(handlerArgument)) {
                strictModeArgumentCheckError(target);
            }
        }
    }

    private void strictModeArgumentCheckError(Target target) {
        String message = "Arguments of handler " + methodNode.name + " do not match target " + target.method.name;
        Injector.logger.error(
            "Injection failure, ArgumentHandlingStrategy.STRICT mode has been enabled due to none of the handler's arguments being annotated with @Arg.",
            message
        );

        throw new InjectionError(message);
    }

    private void injectInvokeCallback(Target target, InjectionNode node) {
        InsnList instructions = new InsnList();
        CallbackInfoHelper callbackInfoHelper = node.getDecoration("callbackInfoHelper");

        // CallbackInfo info = new CallbackInfo(...);
        callbackInfoHelper.generateCallbackInfo(instructions, target, isCancellable);

        // Load the arguments that are desired from the handler
        this.pushDesiredArguments(instructions, target, node, callbackInfoHelper);

        // Add a method call to the handler to the list
        this.invokeHandler(instructions);

        // Wrap the handler invocation in an if(callbackInfo.isCancelled()) check
        if (isCancellable) {
            callbackInfoHelper.wrapInCancellationCheck(instructions, target);
        }

        // Add our instructions before the targeted node
        target.insns.insertBefore(node.getCurrentTarget(), instructions);
    }

    private void pushDesiredArguments(
        InsnList instructions,
        Target target,
        InjectionNode node,
        CallbackInfoHelper callbackInfoHelper
    ) {
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

        for (int i = 0; i < this.methodArgs.length; i++) {
            // We want to get the first argument from the target that matches the current callback argument's type
            Type argumentType = this.methodArgs[i];

            // If the descriptor is CallbackInfo, we need to push it
            if (CallbackInfoUtils.typeIsCallbackInfo(argumentType)) {
                callbackInfoHelper.pushCallbackInfoIfRequired(instructions);
                continue;
            }

            // Check if the index was overridden by @Arg or @Local
            AnnotationNode argNode = Annotations.getVisibleParameter(methodNode, Arg.class, i);
            AnnotationNode localNode = Annotations.getVisibleParameter(methodNode, Local.class, i);

            AnnotationNode annotationNode = argNode != null ? argNode : localNode;
            boolean isArgumentNode = argNode != null;

            if (annotationNode != null) {
                // Push the local from the annotation's data, i.e. find a local based on its ordinal
                this.pushLocalFromAnnotation(
                    instructions,
                    target,
                    node,
                    annotationNode,
                    argumentType,
                    isArgumentNode
                );
            } else {
                // There's no annotation, we should just get the first type from the target's arguments that matches
                // the handler's argument type
                int targetArgumentIndex = targetArguments.indexOf(argumentType);

                instructions.add(
                    new VarInsnNode(
                        argumentType.getOpcode(Opcodes.ILOAD),
                        target.getArgIndices()[targetArgumentIndex]
                    )
                );
            }
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

    /**
     * Loops over each argument in the handler method, and checks if any of them are CallbackInfo(Returnable).
     */
    private boolean isCallbackInfoNeeded() {
        for (Type argumentType : methodArgs) {
            if (CallbackInfoUtils.typeIsCallbackInfo(argumentType)) {
                return true;
            }
        }

        return false;
    }
}
