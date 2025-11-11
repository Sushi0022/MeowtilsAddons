package com.github.tewxx.meowtilsaddons.mixin.meowtils;

import com.github.tewxx.meowtilsaddons.inject.ModuleInjector;
import com.github.tewxx.meowtilsaddons.inject.ModuleManagerRef;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.gen.Invoker;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;
import wtf.tatp.meowtils.gui.Module;
import wtf.tatp.meowtils.modules.utility.AutoFish;

/**
 * Injects addon modules into Meowtils' ModuleManager at construction time.
 *
 * Note: We target by string to avoid compile-time dependency on Meowtils.
 */
@Pseudo
@Mixin(targets = "wtf.tatp.meowtils.gui.ModuleManager", remap = false)
public class MixinModuleManager {

    // Allow calling the private static register(Module... mods) method
    @Invoker("register")
    private static void meowtilsaddons$register(Module... mods) {
        throw new AssertionError();
    }

    @Inject(method = "<init>", at = @At("RETURN"))
    private void meowtilsaddons$afterCtor(CallbackInfo ci) {
        // 'this' is the ModuleManager instance
        ModuleManagerRef.set(this);
        ModuleInjector.inject(this);
    }

    // In case ModuleManager is only used statically, hook the static initializer after it completes
    @Inject(method = "<clinit>", at = @At("RETURN"))
    private static void meowtilsaddons$afterClinit(CallbackInfo ci) {
        try {
            ModuleInjector.injectStatic(Class.forName("wtf.tatp.meowtils.gui.ModuleManager"));
        } catch (Throwable ignored) { }

        // Fallback: ensure our test module is present even if the injector resource failed
        try {
            Class<?> mm = Class.forName("wtf.tatp.meowtils.gui.ModuleManager");
            java.util.List list = null;
            for (java.lang.reflect.Field f : mm.getDeclaredFields()) {
                int mod = f.getModifiers();
                if (!java.lang.reflect.Modifier.isStatic(mod)) continue;
                Class<?> ft = f.getType();
                if (java.util.List.class.isAssignableFrom(ft) || java.util.Collection.class.isAssignableFrom(ft)) {
                    f.setAccessible(true);
                    Object val = f.get(null);
                    if (val instanceof java.util.List) { list = (java.util.List) val; break; }
                }
            }
            if (list != null) {
                Class<?> af = Class.forName("com.github.tewxx.meowtilsaddons.modules.utility.AutoFish");
                boolean present = false;
                for (Object o : list) {
                    if (o != null && o.getClass().getName().equals(af.getName())) { present = true; break; }
                }
                if (!present) {
                    Object inst = af.getDeclaredConstructor().newInstance();
                    list.add(inst);
                    System.out.println("[MeowtilsAddons] Fallback appended AutoFish into ModuleManager modules list");
                }
            }
        } catch (Throwable ignored) { }

        // Explicitly register our module so decompilers show an added call
        try {
            meowtilsaddons$register(new AutoFish());
            System.out.println("[MeowtilsAddons] Invoker-registered AutoFish in ModuleManager.<clinit>");
        } catch (Throwable ignored) { }
    }

    // Removed the getModules() RETURN hook to avoid duplicate appends and toggle spam.
}
