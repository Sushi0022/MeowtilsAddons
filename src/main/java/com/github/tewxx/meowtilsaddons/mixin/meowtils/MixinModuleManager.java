package com.github.tewxx.meowtilsaddons.mixin.meowtils;

import com.github.tewxx.meowtilsaddons.inject.ModuleInjector;
import com.github.tewxx.meowtilsaddons.inject.ModuleManagerRef;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;
import wtf.tatp.meowtils.gui.Module;

/**
 * Injects addon modules into Meowtils' ModuleManager at construction time.
 *
 * Note: We target by string to avoid compile-time dependency on Meowtils.
 */
@Pseudo
@Mixin(targets = "wtf.tatp.meowtils.gui.ModuleManager", remap = false)
public class MixinModuleManager {

    @Inject(method = "<init>", at = @At("RETURN"))
    private void meowtilsaddons$afterCtor(CallbackInfo ci) {
        // 'this' is the ModuleManager instance
        ModuleManagerRef.set(this);
        ModuleInjector.inject(this);

        // Relocate specific modules to Rejects after instance construction as well
        try {
            // Ensure Rejects exists
            Module.Category rejects = null;
            for (Module.Category c : Module.Category.values()) {
                if ("Rejects".equals(c.name())) { rejects = c; break; }
            }
            if (rejects != null) {
                // Load rejects list from bundled resource: meowtilsaddons_modules.txt
                java.util.Set<String> rejectNames = new java.util.HashSet<String>();
                try {
                    java.io.InputStream is = Thread.currentThread().getContextClassLoader().getResourceAsStream("meowtilsaddons_modules.txt");
                    if (is == null) {
                        is = MixinModuleManager.class.getClassLoader().getResourceAsStream("meowtilsaddons_modules.txt");
                    }
                    if (is != null) {
                        java.io.BufferedReader br = new java.io.BufferedReader(new java.io.InputStreamReader(is, java.nio.charset.StandardCharsets.UTF_8));
                        String line;
                        while ((line = br.readLine()) != null) {
                            String s = line.trim();
                            if (s.isEmpty() || s.startsWith("#") || s.startsWith("//")) continue;
                            String[] partsRN = s.split("\\|", 2);
                            String classNameRN = partsRN[0].trim();
                            String simpleRN = classNameRN;
                            int idx = simpleRN.lastIndexOf('.');
                            if (idx >= 0 && idx < simpleRN.length() - 1) simpleRN = simpleRN.substring(idx + 1);
                            rejectNames.add(simpleRN.toLowerCase(java.util.Locale.ROOT));
                        }
                        br.close();
                    }
                } catch (Throwable ignoredLoad) {}
                // Always include sane defaults
                rejectNames.add("speedmine");
                rejectNames.add("autofish");
                rejectNames.add("levelfaker");


                Class<?> mm = Class.forName("wtf.tatp.meowtils.gui.ModuleManager");
                java.lang.reflect.Method getModules = mm.getDeclaredMethod("getModules");
                java.util.List list = (java.util.List) getModules.invoke(null);
                if (list != null) {
                    // 1) Ensure modules from resource are present (auto-register)
                    try {
                        java.io.InputStream is = Thread.currentThread().getContextClassLoader().getResourceAsStream("meowtilsaddons_modules.txt");
                        if (is == null) is = MixinModuleManager.class.getClassLoader().getResourceAsStream("meowtilsaddons_modules.txt");
                        if (is != null) {
                            java.io.BufferedReader br = new java.io.BufferedReader(new java.io.InputStreamReader(is, java.nio.charset.StandardCharsets.UTF_8));
                            String line;
                            while ((line = br.readLine()) != null) {
                                String s = line.trim();
                                if (s.isEmpty() || s.startsWith("#") || s.startsWith("//")) continue;
                                String[] parts = s.split("\\|", 2);
                                String className = parts[0].trim();
                                String catStr = parts.length > 1 ? parts[1].trim() : "advanced";
                                // Skip if already present by class
                                boolean present = false;
                                for (Object m : (java.util.List<?>) list) {
                                    if (m != null && m.getClass().getName().equals(className)) { present = true; break; }
                                }
                                if (!present) {
                                    try {
                                        Class<?> modCls = Class.forName(className);
                                        Object category = null;
                                        try {
                                            for (Module.Category c : Module.Category.values()) {
                                                if (c.name().equalsIgnoreCase(catStr)) { category = c; break; }
                                            }
                                            if (category == null) category = Module.Category.Advanced;
                                        } catch (Throwable ignoredCat) { category = Module.Category.Advanced; }
                                        Object inst = null;
                                        // Try several constructors
                                        try {
                                            java.lang.reflect.Constructor<?> c = modCls.getDeclaredConstructor(String.class, String.class, String.class, Module.Category.class);
                                            c.setAccessible(true);
                                            inst = c.newInstance(modCls.getSimpleName(), "", "", category);
                                        } catch (NoSuchMethodException ignored1) {}
                                        if (inst == null) try {
                                            java.lang.reflect.Constructor<?> c = modCls.getDeclaredConstructor(String.class, String.class, Module.Category.class);
                                            c.setAccessible(true);
                                            inst = c.newInstance(modCls.getSimpleName(), "", category);
                                        } catch (NoSuchMethodException ignored2) {}
                                        if (inst == null) try {
                                            java.lang.reflect.Constructor<?> c = modCls.getDeclaredConstructor(String.class, String.class, String.class, Module.Category.class, boolean.class);
                                            c.setAccessible(true);
                                            inst = c.newInstance(modCls.getSimpleName(), "", "", category, false);
                                        } catch (NoSuchMethodException ignored3) {}
                                        if (inst == null) try {
                                            java.lang.reflect.Constructor<?> c = modCls.getDeclaredConstructor(String.class, String.class, Module.Category.class, boolean.class);
                                            c.setAccessible(true);
                                            inst = c.newInstance(modCls.getSimpleName(), "", category, false);
                                        } catch (NoSuchMethodException ignored4) {}
                                        if (inst == null) try {
                                            java.lang.reflect.Constructor<?> c = modCls.getDeclaredConstructor();
                                            c.setAccessible(true);
                                            inst = c.newInstance();
                                        } catch (NoSuchMethodException ignored5) {}
                                        if (inst != null) {
                                            list.add(inst);
                                            // Force all resource-instantiated modules into Rejects by default
                                            if (rejects != null) {
                                                try {
                                                    java.lang.reflect.Field catField = inst.getClass().getSuperclass().getDeclaredField("category");
                                                    catField.setAccessible(true);
                                                    catField.set(inst, rejects);
                                                } catch (Throwable ignoredSetCat) {}
                                            }
                                            System.out.println("[MeowtilsAddons] Auto-registered module from resources: " + className);
                                        } else {
                                            System.out.println("[MeowtilsAddons] Could not instantiate module class: " + className);
                                        }
                                    } catch (Throwable instErr) {
                                        System.out.println("[MeowtilsAddons] Failed to load module class from resources: " + className + " -> " + instErr);
                                    }
                                }
                            }
                            br.close();
                        }
                    } catch (Throwable ignoredEnsure) {}

                    // 2) Relocate listed modules to Rejects
                    for (Object m : (java.util.List<?>) list) {
                        if (m == null) continue;
                        try {
                            String name = String.valueOf(m.getClass().getMethod("getName").invoke(m));
                            String simple = m.getClass().getSimpleName();
                            // Normalize for matching
                            String low = name.toLowerCase(java.util.Locale.ROOT);
                            String lowSimple = simple == null ? "" : simple.toLowerCase(java.util.Locale.ROOT);
                            boolean listed = rejectNames.contains(low) || rejectNames.contains(lowSimple);
                            if (listed) {
                                java.lang.reflect.Field catField = m.getClass().getSuperclass().getDeclaredField("category");
                                catField.setAccessible(true);
                                catField.set(m, rejects);
                                System.out.println("[MeowtilsAddons] Relocated module '" + name + "' to Category.Rejects (ctor)");
                            }
                        } catch (Throwable ignoredEach) {}
                    }
                }
            }
        } catch (Throwable ignored) { }
    }

    // In case ModuleManager is only used statically, hook the static initializer after it completes
    @Inject(method = "<clinit>", at = @At("RETURN"))
    private static void meowtilsaddons$afterClinit(CallbackInfo ci) {
        try {
            ModuleInjector.injectStatic(Class.forName("wtf.tatp.meowtils.gui.ModuleManager"));
        } catch (Throwable ignored) { }

        // Removed legacy AutoFish fallback to avoid hard compile dependency

        // Move SpeedMine into Category.Rejects at runtime (if present)
        try {
            // Find Rejects enum
            Module.Category rejects = null;
            for (Module.Category c : Module.Category.values()) {
                if ("Rejects".equals(c.name())) { rejects = c; break; }
            }
            if (rejects != null) {
                // Load rejects list from bundled resource
                java.util.Set<String> rejectNames = new java.util.HashSet<String>();
                try {
                    java.io.InputStream is = Thread.currentThread().getContextClassLoader().getResourceAsStream("meowtilsaddons_modules.txt");
                    if (is == null) {
                        is = MixinModuleManager.class.getClassLoader().getResourceAsStream("meowtilsaddons_modules.txt");
                    }
                    if (is != null) {
                        java.io.BufferedReader br = new java.io.BufferedReader(new java.io.InputStreamReader(is, java.nio.charset.StandardCharsets.UTF_8));
                        String line;
                        while ((line = br.readLine()) != null) {
                            String s = line.trim();
                            if (s.isEmpty() || s.startsWith("#")) continue;
                            rejectNames.add(s.toLowerCase(java.util.Locale.ROOT));
                        }
                        br.close();
                    }
                } catch (Throwable ignoredLoad) {}
                rejectNames.add("speedmine");
                rejectNames.add("autofish");
                rejectNames.add("levelfaker");


                // Obtain ModuleManager modules list via static field scan
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
                    // 1) Ensure modules from resources are present
                    try {
                        java.io.InputStream is = Thread.currentThread().getContextClassLoader().getResourceAsStream("meowtilsaddons_modules.txt");
                        if (is == null) is = MixinModuleManager.class.getClassLoader().getResourceAsStream("meowtilsaddons_modules.txt");
                        if (is != null) {
                            java.io.BufferedReader br = new java.io.BufferedReader(new java.io.InputStreamReader(is, java.nio.charset.StandardCharsets.UTF_8));
                            String line;
                            while ((line = br.readLine()) != null) {
                                String s = line.trim();
                                if (s.isEmpty() || s.startsWith("#") || s.startsWith("//")) continue;
                                String[] parts = s.split("\\|", 2);
                                String className = parts[0].trim();
                                String catStr = parts.length > 1 ? parts[1].trim() : "advanced";
                                boolean present = false;
                                for (Object m : (java.util.List<?>) list) {
                                    if (m != null && m.getClass().getName().equals(className)) { present = true; break; }
                                }
                                if (!present) {
                                    try {
                                        Class<?> modCls = Class.forName(className);
                                        Object category = null;
                                        try {
                                            for (Module.Category c : Module.Category.values()) {
                                                if (c.name().equalsIgnoreCase(catStr)) { category = c; break; }
                                            }
                                            if (category == null) category = Module.Category.Advanced;
                                        } catch (Throwable ignoredCat) { category = Module.Category.Advanced; }
                                        Object inst = null;
                                        try {
                                            java.lang.reflect.Constructor<?> c = modCls.getDeclaredConstructor(String.class, String.class, String.class, Module.Category.class);
                                            c.setAccessible(true);
                                            inst = c.newInstance(modCls.getSimpleName(), "", "", category);
                                        } catch (NoSuchMethodException ignored1) {}
                                        if (inst == null) try {
                                            java.lang.reflect.Constructor<?> c = modCls.getDeclaredConstructor(String.class, String.class, Module.Category.class);
                                            c.setAccessible(true);
                                            inst = c.newInstance(modCls.getSimpleName(), "", category);
                                        } catch (NoSuchMethodException ignored2) {}
                                        if (inst == null) try {
                                            java.lang.reflect.Constructor<?> c = modCls.getDeclaredConstructor(String.class, String.class, String.class, Module.Category.class, boolean.class);
                                            c.setAccessible(true);
                                            inst = c.newInstance(modCls.getSimpleName(), "", "", category, false);
                                        } catch (NoSuchMethodException ignored3) {}
                                        if (inst == null) try {
                                            java.lang.reflect.Constructor<?> c = modCls.getDeclaredConstructor(String.class, String.class, Module.Category.class, boolean.class);
                                            c.setAccessible(true);
                                            inst = c.newInstance(modCls.getSimpleName(), "", category, false);
                                        } catch (NoSuchMethodException ignored4) {}
                                        if (inst == null) try {
                                            java.lang.reflect.Constructor<?> c = modCls.getDeclaredConstructor();
                                            c.setAccessible(true);
                                            inst = c.newInstance();
                                        } catch (NoSuchMethodException ignored5) {}
                                        if (inst != null) {
                                            list.add(inst);
                                            // Force all resource-instantiated modules into Rejects by default (static path)
                                            if (rejects != null) {
                                                try {
                                                    java.lang.reflect.Field catField = inst.getClass().getSuperclass().getDeclaredField("category");
                                                    catField.setAccessible(true);
                                                    catField.set(inst, rejects);
                                                } catch (Throwable ignoredSetCat) {}
                                            }
                                            System.out.println("[MeowtilsAddons] Auto-registered module from resources: " + className);
                                        } else {
                                            System.out.println("[MeowtilsAddons] Could not instantiate module class: " + className);
                                        }
                                    } catch (Throwable instErr) {
                                        System.out.println("[MeowtilsAddons] Failed to load module class from resources: " + className + " -> " + instErr);
                                    }
                                }
                            }
                            br.close();
                        }
                    } catch (Throwable ignoredEnsure) {}

                    // 2) Relocate listed modules to Rejects
                    for (Object m : (java.util.List<?>) list) {
                        if (m == null) continue;
                        try {
                            String name = String.valueOf(m.getClass().getMethod("getName").invoke(m));
                            String simple = m.getClass().getSimpleName();
                            String low = name.toLowerCase(java.util.Locale.ROOT);
                            String lowSimple = simple == null ? "" : simple.toLowerCase(java.util.Locale.ROOT);
                            boolean listed = rejectNames.contains(low) || rejectNames.contains(lowSimple);
                            if (listed) {
                                java.lang.reflect.Field catField = m.getClass().getSuperclass().getDeclaredField("category");
                                catField.setAccessible(true);
                                catField.set(m, rejects);
                                System.out.println("[MeowtilsAddons] Moved " + name + " to Category.Rejects");
                            }
                        } catch (Throwable ignoredEach) {}
                    }
                }
            }
        } catch (Throwable ignoredMove) { }
    }

    // Removed the getModules() RETURN hook to avoid duplicate appends and toggle spam.
}
