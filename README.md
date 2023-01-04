# BetterInject

BetterInject provides a new `@Inject` annotation to use with [Mixin](https://github.com/SpongePowered/Mixin/).

With the regular `@Inject`, you need to either:

- Have all method arguments
- Have no method arguments

This isn't ideal, and in many cases you'll end up declaring tons of parameters that you won't even use, this can even
make cross-version development a bit more difficult.

BetterInject's `@Inject` solves this problem, it:

- Uses the same name for readability (under a different package of course)
- Improves local capture (`@Local` annotation)
- Doesn't require you to always have CallbackInfo
- Doesn't require you to always have either all arguments, or none, you can pick and choose

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

2. Single argument

   `@Arg` is required when you are not using all of the arguments provided by the target method. If you don't
   use `@Arg`, strict mode will be enabled. BetterInject will throw an exception if your handler method's arguments dont
   match the target method's.

    ```java
    @Inject(method = "render", at = @At("HEAD"), cancellable = true)
    public void myMod$onRender(@Arg float delta) {
    
    }
    ```

   > **Warning**
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

### Local Capture

Local capture has been made much easier. You can now just apply `@Local(ordinal=)` on to an argument in your `@Inject`
method's signature!

```java
@Inject(method = "render", at = @At("RETURN"))
public void myMod$onRender(
    @Local String localString // Default ordinal of -1 (a.k.a. there is only one String in the local variables of this target)
) {
    System.out.println("Grabbed local: " + localString);
}
```

> **Warning**
> `@Local` without an ordinal (defaults at `-1`) behaves just like `@Arg`. If there is less than or more than 1 local variable with the same type as the one you are targetting, an exception will be thrown at runtime.

### Initializing

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
