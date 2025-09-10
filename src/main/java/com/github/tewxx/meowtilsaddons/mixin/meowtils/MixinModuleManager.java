package com.example.mixin.meowtils;

import com.example.InjectionState;
import com.example.runtime.ModuleInjector;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Fast-path visual injection: append placeholder Module instances at the top of the
 * ModuleManager list so they show up in the Meowtils GUI. These instances are based on
 * the existing GUI module class and have their name changed via Module#setName(String).
 *
 * This does not generate new classes; it only creates additional instances so they
 * appear in lists immediately. Behavior may be limited until we implement a full
 * runtime-generated subclass.
 */
@Pseudo
@Mixin(targets = "wtf.tatp.meowtils.gui.ModuleManager", remap = false)
public class MixinModuleManager {

    // Tracks names we've already injected to avoid duplicating each getModules() call
    private static final Set<String> injectedOnce = new HashSet<String>();

    @Inject(method = "getModules", at = @At("RETURN"), cancellable = false)
    private static void onGetModulesReturn(CallbackInfoReturnable<ArrayList> cir) {
        try {
            ArrayList list = cir.getReturnValue();
            if (list == null) return;

            // Build a set of existing names
            Set<String> existing = new HashSet<String>();
            for (Object mod : (List) list) {
                String name = getModuleName(mod);
                if (name != null) existing.add(name);
            }

            // Iterate user-requested injected module names (front to back so first stays on top)
            synchronized (InjectionState.meowtilsInjectedModuleNames) {
                for (int i = InjectionState.meowtilsInjectedModuleNames.size() - 1; i >= 0; i--) {
                    String desiredName = InjectionState.meowtilsInjectedModuleNames.get(i);
                    if (desiredName == null || desiredName.isEmpty()) continue;
                    if (existing.contains(desiredName)) continue; // already present
                    if (injectedOnce.contains(desiredName)) continue; // avoid duplicating per call

                    // Create or load a real runtime class under wtf.tatp.meowtils.modules with this exact name
                    String category = InjectionState.meowtilsInjectedModuleCategories.get(desiredName);
                    if (category == null || category.trim().isEmpty()) category = "Utility";
                    Object newModule = ModuleInjector.createOrLoadRuntimeModule(desiredName, desiredName, category, 0);
                    if (newModule == null) continue;

                    // Insert at the top so it appears first
                    list.add(0, newModule);
                    injectedOnce.add(desiredName);
                    existing.add(desiredName);
                }
            }
        } catch (Throwable t) {
            System.out.println("[MeowtilsInjectors][DEBUG] MixinModuleManager failed to inject visual modules: " + t);
            t.printStackTrace();
        }
    }
    private static String getModuleName(Object module) {
        try {
            Method m = module.getClass().getMethod("getName");
            Object r = m.invoke(module);
            return r == null ? null : String.valueOf(r);
        } catch (Throwable t) {
            return null;
        }
    }
}
