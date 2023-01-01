package dev.cbyrne.betterinject;

import dev.cbyrne.betterinject.injector.InjectInjectionInfo;
import org.spongepowered.asm.mixin.injection.struct.InjectionInfo;

public class BetterInject {
    private static boolean initialized = false;

    public static void initialize() {
        if (initialized) {
            return;
        }

        InjectionInfo.register(InjectInjectionInfo.class);
        initialized = true;
    }
}
