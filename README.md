# BetterInject

BetterInject provides a new `@Inject` annotation to use with [Mixin](https://github.com/SpongePowered/Mixin/).

The main "issue" that arises when using `@Inject` from Mixin is how you write your handler methods. Take the following example:
```java
@Inject(...)
private void handler(MatrixStack stack, int mouseX, int mouseY, float delta, CallbackInfo ci) {
    MyMod.onRender(mouseX, mouseY);
}
```

In this example, we have a handler function that only uses 2 out of the 5 desired arguments. With BetterInject's `@Inject` annotation, we only define what we need:

```java
@Inject(...)
private void handler(
    @Arg(ordinal = 0) int mouseX, 
    @Arg(ordinal = 1) int mouseY
) {
    MyMod.onRender(mouseX, mouseY);
}
```

There are many more benefits to using BetterInject, which you will find as you go through this README.

## Goals

`@Inject` is a very commonly-used injector in Mixin. BetterInject aims to provide utilities to improve this injector. We do replace it with our own annotation, but that annotation is designed to work **mostly** as a drop-in-replacement to Mixin's `@Inject`.

The main improvements that BetterInject provides over Mixin's Inject is:
- Having complete control over the arguments in your handler
- Easy to declare, and more expressive local capture

This is open to expansion though! If you have any suggestions, open an issue, or better yet, a pull request!

> **Note**
> The only feature that will require some porting from Mixin's `@Inject` is if you use their local capture. To see how BetterInject improves local capture, go to the [Local Capture](#local-capture) section.

## Non-goals

The intention of BetterInject is not to overthrow Mixin, or make our own Mixin library. We are simply making an already great library, even better.

## Including in your project

You can get BetterInject from [JitPack](https://jitpack.io).

```groovy
implementation("com.github.cbyrneee:BetterInject:0.1.0")
annotationProcessor("com.github.cbyrneee:BetterInject:0.1.0")
```

For how to initialize BetterInject, check the [Initializing](#initializing) section.

## Examples

For these examples, we will be `@Inject`ing into a method with the following signature:

```java
public void render(MatrixStack stack, int mouseX, int mouseY, float delta)
```

### Basic hook (no arguments or callback info)

```java
@Inject(method = "render", at = @At("HEAD"))
public void myMod$onRender() {
    EventBus.getInstance().post(new MyRenderEvent());
}
```

### Cancelling a method

```java
@Inject(method = "render", at = @At("HEAD"), cancellable = true)
public void myMod$onRender(CallbackInfo ci) {
    boolean cancelled = EventBus.getInstance().postCancellable(new MyRenderEvent());
    
    if (cancelled) {
        ci.cancel();
    }
}
```

### Getting arguments

1. All arguments

    ```java
    @Inject(method = "render", at = @At("HEAD"), cancellable = true)
    public void myMod$onRender(MatrixStack stack, int mouseX, int mouseY, float delta) {
    }
    ```

2. A single argument

   `@Arg` is required when you are not using all of the arguments provided by the target method. If you don't
   use `@Arg`, strict mode will be enabled. BetterInject will throw an exception if your handler method's arguments dont
   match the target method's.

    ```java
    @Inject(method = "render", at = @At("HEAD"), cancellable = true)
    public void myMod$onRender(@Arg float delta) {
    }
    ```

   > **Note**
   > `@Arg` without specifying an ordinal (defaulting at `-1`) assumes that there is only one parameter of that type on the target function. If there is
   more than one, an exception will be thrown at runtime.

3. Multiple arguments

   Use `@Arg(ordinal=)` to specify the ordinal of the argument that you are targeting.

    ```java
    @Inject(method = "render", at = @At("HEAD"), cancellable = true)
    public void myMod$onRender(
        @Arg MatrixStack stack,
        // @Arg(ordinal = 0) int mouseX <- If you wanted mouseX
        @Arg(ordinal = 1) int mouseY
    ) {
    }
    ```

## Local Capture

> **Warning**
> This is where BetterInject is not compatible with Mixin's local capture. You must adapt your handler methods to the new format.

Local capture has improved with BetterInject.
You can now just apply `@Local(ordinal=)` on to an argument in your `@Inject` method's signature!

```java
@Inject(method = "render", at = @At("RETURN"))
public void myMod$onRender(
    @Local String localString // Default ordinal of -1 (a.k.a. there is only one String in the local variables of this target)
) {
    System.out.println("Grabbed local: " + localString);
}
```

> **Note**
> `@Local` without an ordinal (defaults at `-1`) behaves just like `@Arg`. If there is less than or more than 1 local variable with the same type as the one you are targetting, an exception will be thrown at runtime.

## Initializing in your mod

* Fabric
    ```java
    package my.mod;
    
    import dev.cbyrne.betterinject.BetterInject;
    import net.fabricmc.loader.api.entrypoint.PreLaunchEntrypoint;
  
    public class PreLaunch implements PreLaunchEntrypoint {
        @Override
        public void onPreLaunch() {
            BetterInject.initialize();
        }
    }
    ```

    ```json
    {
      ...
      "entrypoints": {
        "preLaunch": [
          "my.mod.PreLaunch"
        ]
      }
    }
    ```
