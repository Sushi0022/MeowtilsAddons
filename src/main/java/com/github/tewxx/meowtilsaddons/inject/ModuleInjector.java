package com.github.tewxx.meowtilsaddons.inject;

import net.minecraftforge.fml.common.Loader;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.File;
import java.io.FileWriter;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import com.github.tewxx.meowtilsaddons.inject.ModuleManagerRef;
import java.util.HashSet;
import java.util.Set;

public final class ModuleInjector {
    private static final String RESOURCE_NAME = "meowtilsaddons_modules.txt";
    private static final String MODULE_BASE_CLASS = "wtf.tatp.meowtils.gui.Module";
    // Avoid duplicate instantiation/registration across repeated hooks
    private static final Set<String> SEEN_CLASSES = new HashSet<>();

    private ModuleInjector() {}
 
    public static void inject(Object moduleManagerInstance) {
        try {
            if (!Loader.isModLoaded("meowtils")) {
                log("Target mod 'meowtils' not loaded, skipping injector");
                return;
            }
            
            Class<?> moduleBase = Class.forName(MODULE_BASE_CLASS);
            List<Object> toAdd = loadConfiguredModules(moduleBase);
            if (toAdd.isEmpty()) {
                log("No modules to inject. (%s not found or empty)", RESOURCE_NAME);
                // Even if we didn't add any, still produce a debug dump for visibility
                dumpModules(moduleManagerInstance, moduleBase, "no-configured-modules");
                return;
            }

            int added = tryMethodRegistration(moduleManagerInstance, moduleBase, toAdd);
            if (added < toAdd.size()) {
                added += tryListFieldAppend(moduleManagerInstance, moduleBase, toAdd.subList(added, toAdd.size()));
            }
            log("Injected %d/%d module(s) into ModuleManager", added, toAdd.size());

            // Always dump state after attempting injection
            dumpModules(moduleManagerInstance, moduleBase, "post-inject");
        } catch (Throwable t) {
            log("Injection failed: %s", String.valueOf(t));
            t.printStackTrace();
        }
    }

    /**
     * Injection path for when ModuleManager uses static state only.
     */
    public static void injectStatic(Class<?> moduleManagerClass) {
        try {
            if (!Loader.isModLoaded("meowtils")) {
                log("Target mod 'meowtils' not loaded, skipping static injector");
                return;
            }
            Class<?> moduleBase = Class.forName(MODULE_BASE_CLASS);
            List<Object> toAdd = loadConfiguredModules(moduleBase);
            if (toAdd.isEmpty()) {
                log("No modules to inject (static). (%s not found or empty)", RESOURCE_NAME);
                dumpModulesStatic(moduleManagerClass, moduleBase, "no-configured-modules");
                return;
            }

            int added = tryStaticMethodRegistration(moduleManagerClass, moduleBase, toAdd);
            if (added < toAdd.size()) {
                added += tryStaticListFieldAppend(moduleManagerClass, moduleBase, toAdd.subList(added, toAdd.size()));
            }
            log("(static) Injected %d/%d module(s) into ModuleManager", added, toAdd.size());
            dumpModulesStatic(moduleManagerClass, moduleBase, "post-inject");
        } catch (Throwable t) {
            log("Static injection failed: %s", String.valueOf(t));
        }
    }

    private static int tryStaticListFieldAppend(Class<?> mmClass, Class<?> moduleBase, List<Object> modules) {
        List<Field> candidates = new ArrayList<>();
        for (Field f : mmClass.getDeclaredFields()) {
            int mod = f.getModifiers();
            if (!Modifier.isStatic(mod)) continue;
            if (List.class.isAssignableFrom(f.getType()) || Collection.class.isAssignableFrom(f.getType())) {
                candidates.add(f);
            }
        }
        candidates.sort(Comparator.comparingInt(f -> nameScore(f.getName().toLowerCase(Locale.ROOT))));

        int added = 0;
        for (Field f : candidates) {
            try {
                f.setAccessible(true);
                Object val = f.get(null);
                if (!(val instanceof List)) continue;
                @SuppressWarnings("unchecked")
                List<Object> list = (List<Object>) val;
                boolean looksRight = list.isEmpty() || list.stream().filter(Objects::nonNull).anyMatch(moduleBase::isInstance);
                if (!looksRight) continue;

                for (Object m : modules) {
                    if (!containsClass(list, m.getClass())) {
                        list.add(m);
                        added++;
                    }
                }
                if (added > 0) {
                    log("(static) Appended %d module(s) into field '%s'", added, f.getName());
                    break;
                }
            } catch (Throwable ignored) { }
        }
        return added;
    }

    private static int tryStaticMethodRegistration(Class<?> mmClass, Class<?> moduleBase, List<Object> modules) {
        Method register = null;
        for (Method m : mmClass.getDeclaredMethods()) {
            try {
                if (!Modifier.isStatic(m.getModifiers())) continue;
                if (m.getParameterCount() != 1) continue;
                if (m.getReturnType() != Void.TYPE) continue;
                Class<?> param = m.getParameterTypes()[0];
                if (!param.isAssignableFrom(moduleBase)) continue;
                m.setAccessible(true);
                register = m;
                break;
            } catch (Throwable ignored) { }
        }
        if (register == null) return 0;
        int added = 0;
        for (Object mod : modules) {
            if (mod == null) continue;
            try {
                // Skip if a module of the same class is already present
                if (isPresentStatic(mmClass, moduleBase, mod.getClass().getName())) continue;
                register.invoke(null, mod);
                added++;
            } catch (Throwable ignored) { }
        }
        return added;
    }

    private static boolean isPresentStatic(Class<?> mmClass, Class<?> moduleBase, String className) {
        try {
            // Probe static List fields for existing modules
            for (Field f : mmClass.getDeclaredFields()) {
                int mod = f.getModifiers();
                if (!Modifier.isStatic(mod)) continue;
                if (!(List.class.isAssignableFrom(f.getType()) || Collection.class.isAssignableFrom(f.getType()))) continue;
                f.setAccessible(true);
                Object val = f.get(null);
                if (val instanceof List) {
                    @SuppressWarnings("unchecked") List<Object> list = (List<Object>) val;
                    for (Object o : list) if (o != null && o.getClass().getName().equals(className)) return true;
                }
            }
        } catch (Throwable ignored) { }
        return false;
    }

    private static List<Object> loadConfiguredModules(Class<?> moduleBase) {
        List<Object> list = new ArrayList<>();
        ClassLoader cl = Thread.currentThread().getContextClassLoader();
        if (cl == null) cl = ModuleInjector.class.getClassLoader();
        try (InputStream in = cl.getResourceAsStream(RESOURCE_NAME)) {
            if (in == null) {
                log("Resource not found: %s", RESOURCE_NAME);
                return list;
            }
            try (BufferedReader br = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
                String line;
                int ln = 0;
                while ((line = br.readLine()) != null) {
                    ln++;
                    line = line.trim();
                    if (line.isEmpty() || line.startsWith("#") || line.startsWith("//")) continue;
                    String[] parts = line.split("\\|", 2);
                    String className = parts[0].trim();
                    String category = parts.length > 1 ? parts[1].trim() : "";
                    try {
                        Class<?> cls = Class.forName(className);
                        if (!moduleBase.isAssignableFrom(cls)) {
                            log("Line %d: %s does not extend %s, skipping", ln, className, MODULE_BASE_CLASS);
                            continue;
                        }
                        Object instance = instantiate(cls);
                        if (instance != null) {
                            list.add(instance);
                            if (!category.isEmpty()) {
                                log("Prepared module %s | category=%s", cls.getName(), category);
                            } else {
                                log("Prepared module %s", cls.getName());
                            }
                        }
                    } catch (ClassNotFoundException e) {
                        log("Line %d: Class not found %s", ln, className);
                    } catch (Throwable t) {
                        log("Line %d: Failed to construct %s: %s", ln, className, t.getClass().getSimpleName());
                    }
                }
            }
        } catch (IOException ioe) {
            log("Failed reading %s: %s", RESOURCE_NAME, ioe.getMessage());
        }
        return list;
    }

    private static Object instantiate(Class<?> cls) throws NoSuchMethodException, InvocationTargetException, InstantiationException, IllegalAccessException {
        String name = cls.getName();
        if (SEEN_CLASSES.contains(name)) return null; // already constructed once
        try {
            Object inst;
            try {
                inst = cls.getDeclaredConstructor().newInstance();
            } catch (NoSuchMethodException e) {
                // try no-arg public constructor via getConstructor
                inst = cls.getConstructor().newInstance();
            }
            SEEN_CLASSES.add(name);
            return inst;
        } catch (NoSuchMethodException e2) {
            throw e2;
        }
    }

    private static int tryMethodRegistration(Object moduleManagerInstance, Class<?> moduleBase, List<Object> modules) {
        Method registerMethod = findRegistrationMethod(moduleManagerInstance.getClass(), moduleBase);
        if (registerMethod == null) return 0;
        int added = 0;
        for (Object m : modules) {
            try {
                registerMethod.invoke(moduleManagerInstance, m);
                added++;
            } catch (Throwable t) {
                log("Failed to register via method for %s: %s", m.getClass().getName(), t.getClass().getSimpleName());
            }
        }
        return added;
    }

    private static Method findRegistrationMethod(Class<?> mmClass, Class<?> moduleBase) {
        Method best = null;
        for (Method m : mmClass.getDeclaredMethods()) {
            try {
                if (m.getParameterCount() != 1) continue;
                if (m.getReturnType() != Void.TYPE) continue;
                Class<?> param = m.getParameterTypes()[0];
                if (!param.isAssignableFrom(moduleBase)) continue;
                m.setAccessible(true);
                // Prefer method names with module semantics
                if (best == null) {
                    best = m;
                } else {
                    best = betterRegistrationMethod(best, m);
                }
            } catch (Throwable ignored) { }
        }
        if (best != null) log("Using registration method: %s", best.getName());
        return best;
    }

    private static Method betterRegistrationMethod(Method a, Method b) {
        String an = a.getName().toLowerCase(Locale.ROOT);
        String bn = b.getName().toLowerCase(Locale.ROOT);
        int as = scoreMethodName(an);
        int bs = scoreMethodName(bn);
        return bs > as ? b : a;
    }

    private static int scoreMethodName(String n) {
        int s = 0;
        if (n.contains("module")) s += 2;
        if (n.contains("add")) s += 1;
        if (n.contains("register")) s += 1;
        return s;
    }

    private static int tryListFieldAppend(Object moduleManagerInstance, Class<?> moduleBase, List<Object> modules) {
        List<Field> candidates = new ArrayList<>();
        for (Field f : moduleManagerInstance.getClass().getDeclaredFields()) {
            if (List.class.isAssignableFrom(f.getType()) || Collection.class.isAssignableFrom(f.getType())) {
                candidates.add(f);
            }
        }
        // Prefer fields hinting at modules in their name
        candidates.sort(Comparator.comparingInt(f -> nameScore(f.getName().toLowerCase(Locale.ROOT))));
        if (candidates.isEmpty()) {
            log("No List/Collection field found on ModuleManager");
            return 0;
        }

        int added = 0;
        for (Field f : candidates) {
            try {
                f.setAccessible(true);
                Object val = f.get(moduleManagerInstance);
                if (!(val instanceof List)) continue;
                @SuppressWarnings("unchecked")
                List<Object> list = (List<Object>) val;
                // Validate list contents when possible
                boolean looksRight = list.isEmpty() || list.stream().filter(Objects::nonNull).anyMatch(moduleBase::isInstance);
                if (!looksRight) continue;

                for (Object m : modules) {
                    if (!containsClass(list, m.getClass())) {
                        list.add(m);
                        added++;
                    }
                }
                if (added > 0) {
                    log("Appended %d module(s) into field '%s'", added, f.getName());
                    break;
                }
            } catch (Throwable ignored) { }
        }
        return added;
    }

    private static int nameScore(String n) {
        int s = 0;
        if (n.contains("module")) s += 4;
        if (n.contains("list")) s += 2;
        if (n.contains("mods")) s += 1;
        return -s; // for ascending sort in comparator above
    }

    private static boolean containsClass(List<Object> list, Class<?> cls) {
        for (Object o : list) {
            if (o != null && o.getClass().getName().equals(cls.getName())) return true;
        }
        return false;
    }

    private static void dumpModules(Object moduleManagerInstance, Class<?> moduleBase, String tag) {
        try {
            List<Object> modules = findModuleList(moduleManagerInstance, moduleBase);
            if (modules == null) {
                log("Debug dump: could not locate modules list on ModuleManager");
                return;
            }

            File baseDir = new File(System.getProperty("user.home"), "Downloads");
            File outDir = new File(baseDir, "MeowtilsAddons-debug");
            if (!outDir.exists() && !outDir.mkdirs()) {
                log("Debug dump: failed to create directory %s", outDir.getAbsolutePath());
                return;
            }
            String fileName = String.format(Locale.ROOT, "modules-%s-%d.txt", tag == null ? "dump" : tag, System.currentTimeMillis());
            File outFile = new File(outDir, fileName);

            try (BufferedWriter bw = new BufferedWriter(new FileWriter(outFile))) {
                bw.write("MeowtilsAddons Module Dump\n");
                bw.write(String.format(Locale.ROOT, "Total modules detected: %d\n\n", modules.size()));
                int idx = 0;
                for (Object m : modules) {
                    if (m == null) continue;
                    String cls = m.getClass().getName();
                    String name = firstNonEmpty(
                        invokeString(m, "getName", "name"),
                        getFieldString(m, "name")
                    );
                    String category = firstNonEmpty(
                        invokeString(m, "getCategory", "getCategoryName"),
                        getFieldString(m, "category")
                    );
                    String enabled = firstNonEmpty(
                        invokeString(m, "isEnabled", "getEnabled"),
                        null
                    );
                    bw.write(String.format(Locale.ROOT,
                        "[%03d] class=%s name=%s category=%s enabled=%s\n",
                        idx++, cls, safe(name), safe(category), safe(enabled)));
                }
            }
            log("Debug dump written: %s", outFile.getAbsolutePath());
        } catch (Throwable t) {
            log("Debug dump failed: %s", String.valueOf(t));
        }
    }

    private static void dumpModulesStatic(Class<?> mmClass, Class<?> moduleBase, String tag) {
        try {
            List<Object> modules = findStaticModuleList(mmClass, moduleBase);
            if (modules == null) {
                log("Debug dump (static): could not locate modules list on ModuleManager");
                return;
            }

            File baseDir = new File(System.getProperty("user.home"), "Downloads");
            File outDir = new File(baseDir, "MeowtilsAddons-debug");
            if (!outDir.exists() && !outDir.mkdirs()) {
                log("Debug dump (static): failed to create directory %s", outDir.getAbsolutePath());
                return;
            }
            String fileName = String.format(Locale.ROOT, "modules-%s-%d.txt", tag == null ? "dump" : tag, System.currentTimeMillis());
            File outFile = new File(outDir, fileName);

            try (BufferedWriter bw = new BufferedWriter(new FileWriter(outFile))) {
                bw.write("MeowtilsAddons Module Dump (static)\n");
                bw.write(String.format(Locale.ROOT, "Total modules detected: %d\n\n", modules.size()));
                int idx = 0;
                for (Object m : modules) {
                    if (m == null) continue;
                    String cls = m.getClass().getName();
                    String name = firstNonEmpty(
                        invokeString(m, "getName", "name"),
                        getFieldString(m, "name")
                    );
                    String category = firstNonEmpty(
                        invokeString(m, "getCategory", "getCategoryName"),
                        getFieldString(m, "category")
                    );
                    String enabled = firstNonEmpty(
                        invokeString(m, "isEnabled", "getEnabled"),
                        null
                    );
                    bw.write(String.format(Locale.ROOT,
                        "[%03d] class=%s name=%s category=%s enabled=%s\n",
                        idx++, cls, safe(name), safe(category), safe(enabled)));
                }
            }
            log("Debug dump (static) written: %s", outFile.getAbsolutePath());
        } catch (Throwable t) {
            log("Debug dump (static) failed: %s", String.valueOf(t));
        }
    }

    /**
     * Public entry-point for manual dumping via command.
     * @param tag Optional tag to include in file name (e.g. "manual").
     * @return Absolute path to the dump file, or null if failed.
     */
    public static String dumpNow(String tag) {
        try {
            if (!Loader.isModLoaded("meowtils")) {
                log("Manual dump: target mod 'meowtils' not loaded");
                return null;
            }
            Object mm = ModuleManagerRef.get();
            Class<?> moduleBase = Class.forName(MODULE_BASE_CLASS);
            List<Object> modules = null;
            if (mm != null) {
                modules = findModuleList(mm, moduleBase);
            }
            if (modules == null) {
                // Try static fallback
                try {
                    Class<?> mmClass = Class.forName("wtf.tatp.meowtils.gui.ModuleManager");
                    modules = findStaticModuleList(mmClass, moduleBase);
                } catch (Throwable ignored) { }
            }
            if (modules == null) {
                log("Manual dump: could not locate modules list on ModuleManager (instance or static) yet");
                return null;
            }

            File baseDir = new File(System.getProperty("user.home"), "Downloads");
            File outDir = new File(baseDir, "MeowtilsAddons-debug");
            if (!outDir.exists() && !outDir.mkdirs()) {
                log("Manual dump: failed to create directory %s", outDir.getAbsolutePath());
                return null;
            }
            String fileName = String.format(Locale.ROOT, "modules-%s-%d.txt", (tag == null || tag.isEmpty()) ? "manual" : tag, System.currentTimeMillis());
            File outFile = new File(outDir, fileName);

            try (BufferedWriter bw = new BufferedWriter(new FileWriter(outFile))) {
                bw.write("MeowtilsAddons Module Dump\n");
                bw.write(String.format(Locale.ROOT, "Total modules detected: %d\n\n", modules.size()));
                int idx = 0;
                for (Object m : modules) {
                    if (m == null) continue;
                    String cls = m.getClass().getName();
                    String name = firstNonEmpty(
                        invokeString(m, "getName", "name"),
                        getFieldString(m, "name")
                    );
                    String category = firstNonEmpty(
                        invokeString(m, "getCategory", "getCategoryName"),
                        getFieldString(m, "category")
                    );
                    String enabled = firstNonEmpty(
                        invokeString(m, "isEnabled", "getEnabled"),
                        null
                    );
                    bw.write(String.format(Locale.ROOT,
                        "[%03d] class=%s name=%s category=%s enabled=%s\n",
                        idx++, cls, safe(name), safe(category), safe(enabled)));
                }
            }
            log("Manual debug dump written: %s", outFile.getAbsolutePath());
            return outFile.getAbsolutePath();
        } catch (Throwable t) {
            log("Manual dump failed: %s", String.valueOf(t));
            return null;
        }
    }

    private static List<Object> findModuleList(Object moduleManagerInstance, Class<?> moduleBase) {
        try {
            List<Field> candidates = new ArrayList<>();
            for (Field f : moduleManagerInstance.getClass().getDeclaredFields()) {
                if (List.class.isAssignableFrom(f.getType()) || Collection.class.isAssignableFrom(f.getType())) {
                    candidates.add(f);
                }
            }
            candidates.sort(Comparator.comparingInt(f -> nameScore(f.getName().toLowerCase(Locale.ROOT))));
            for (Field f : candidates) {
                try {
                    f.setAccessible(true);
                    Object val = f.get(moduleManagerInstance);
                    if (val instanceof List) {
                        @SuppressWarnings("unchecked")
                        List<Object> list = (List<Object>) val;
                        if (list.isEmpty() || list.stream().filter(Objects::nonNull).anyMatch(moduleBase::isInstance)) {
                            return list;
                        }
                    }
                } catch (Throwable ignored) { }
            }
        } catch (Throwable ignored) { }
        return null;
    }

    private static List<Object> findStaticModuleList(Class<?> mmClass, Class<?> moduleBase) {
        try {
            List<Field> candidates = new ArrayList<>();
            for (Field f : mmClass.getDeclaredFields()) {
                int mod = f.getModifiers();
                if (!Modifier.isStatic(mod)) continue;
                if (List.class.isAssignableFrom(f.getType()) || Collection.class.isAssignableFrom(f.getType())) {
                    candidates.add(f);
                }
            }
            candidates.sort(Comparator.comparingInt(f -> nameScore(f.getName().toLowerCase(Locale.ROOT))));
            for (Field f : candidates) {
                try {
                    f.setAccessible(true);
                    Object val = f.get(null);
                    if (val instanceof List) {
                        @SuppressWarnings("unchecked")
                        List<Object> list = (List<Object>) val;
                        if (list.isEmpty() || list.stream().filter(Objects::nonNull).anyMatch(moduleBase::isInstance)) {
                            return list;
                        }
                    }
                } catch (Throwable ignored) { }
            }
        } catch (Throwable ignored) { }
        return null;
    }

    private static String invokeString(Object obj, String... methods) {
        for (String m : methods) {
            if (m == null) continue;
            try {
                Method mm = obj.getClass().getMethod(m);
                Object val = mm.invoke(obj);
                if (val != null) return String.valueOf(val);
            } catch (Throwable ignored) { }
        }
        return null;
    }

    private static String getFieldString(Object obj, String... fields) {
        for (String f : fields) {
            if (f == null) continue;
            try {
                Field ff = obj.getClass().getDeclaredField(f);
                ff.setAccessible(true);
                Object val = ff.get(obj);
                if (val != null) return String.valueOf(val);
            } catch (Throwable ignored) { }
        }
        return null;
    }

    private static String firstNonEmpty(String... vals) {
        if (vals == null) return null;
        for (String v : vals) {
            if (v != null) {
                String t = v.trim();
                if (!t.isEmpty() && !"null".equalsIgnoreCase(t)) return t;
            }
        }
        return null;
    }

    private static String safe(String s) {
        return s == null ? "?" : s;
    }

    private static void log(String fmt, Object... args) {
        try {
            System.out.println("[MeowtilsAddons] " + String.format(Locale.ROOT, fmt, args));
        } catch (Throwable ignored) {
            // last resort
        }
    }
}
