package com.example;

import com.example.command.InjectionCommand;
import com.example.runtime.ModuleInjector;
import net.minecraftforge.client.ClientCommandHandler;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.common.Loader;

@Mod(modid = "examplemod", useMetadata = true)
public class InjectionMod {
    @Mod.EventHandler
    public void init(FMLInitializationEvent event) {
        ClientCommandHandler.instance.registerCommand(new InjectionCommand());
        // Load injected modules from our config at startup (names only).
        // We no longer mutate Meowtils' ModuleManager list here to avoid Meowtils
        // persisting our injected modules into its own config (which caused resets).
        try {
            java.io.File configDir = Loader.instance().getConfigDir();
            java.io.File meowCfgDir = new java.io.File(configDir, "MeowtilsInjectors");
            if (!meowCfgDir.exists()) meowCfgDir.mkdirs();
            java.io.File modulesTxt = new java.io.File(meowCfgDir, "modules.txt");
            if (modulesTxt.exists()) {
                java.util.List<String> lines = java.nio.file.Files.readAllLines(modulesTxt.toPath(), java.nio.charset.StandardCharsets.UTF_8);
                for (String raw : lines) {
                    String line = raw == null ? null : raw.trim();
                    if (line == null || line.isEmpty() || line.startsWith("#")) continue;
                    String name = line;
                    String category = "Utility";
                    int bar = line.lastIndexOf('|');
                    if (bar > 0 && bar < line.length() - 1) {
                        name = line.substring(0, bar).trim();
                        category = line.substring(bar + 1).trim();
                        if (category.isEmpty()) category = "Utility";
                    }
                    // Translate legacy colors &x -> §x (display only) for name
                    name = name.replaceAll("&([0-9a-fk-orA-FK-OR])", "\u00A7$1");
                    // Add to runtime state list
                    synchronized (InjectionState.meowtilsInjectedModuleNames) {
                        if (!InjectionState.meowtilsInjectedModuleNames.contains(name)) {
                            InjectionState.meowtilsInjectedModuleNames.add(name);
                        }
                    }
                    InjectionState.meowtilsInjectedModuleCategories.put(name, category);
                }
                System.out.println("[MeowtilsInjectors][DEBUG] Startup injected module names loaded: " + InjectionState.meowtilsInjectedModuleNames.size());
            } else {
                // Create a template file
                java.nio.file.Files.write(modulesTxt.toPath(), (
                        "# MeowtilsInjectors startup modules\n" +
                        "# One entry per line. Colors with & will be translated for the name.\n" +
                        "# Format: <name>[|<category>]  (category defaults to Utility)\n" +
                        "# Examples:\n" +
                        "# AutoGL\n" +
                        "# Test Module|Hypixel\n"
                ).getBytes(java.nio.charset.StandardCharsets.UTF_8));
                System.out.println("[MeowtilsInjectors][DEBUG] Created config file: " + modulesTxt.getAbsolutePath());
            }
        } catch (Throwable t) {
            System.out.println("[MeowtilsInjectors][DEBUG] Error loading startup modules: " + t);
        }
        // Diagnostic: verify target Meowtils classes/methods exist at runtime
        try {
            System.out.println("[MeowtilsInjectors][DIAG] ClassLoader=" + InjectionMod.class.getClassLoader());
            Class<?> clsCmd = Class.forName("wtf.tatp.meowtils.commands.MeowtilsCommand", false, InjectionMod.class.getClassLoader());
            System.out.println("[MeowtilsInjectors][DIAG] Found class: " + clsCmd + " via CL=" + clsCmd.getClassLoader());
        } catch (Throwable t) {
            System.out.println("[MeowtilsInjectors][DIAG] Could not load wtf.tatp.meowtils.commands.MeowtilsCommand: " + t);
        }
        try {
            Class<?> clsRoot = Class.forName("wtf.tatp.meowtils.Meowtils", false, InjectionMod.class.getClassLoader());
            System.out.println("[MeowtilsInjectors][DIAG] Found class: " + clsRoot + " via CL=" + clsRoot.getClassLoader());
            try {
                java.lang.reflect.Method m = clsRoot.getDeclaredMethod("addCleanMessage", String.class);
                System.out.println("[MeowtilsInjectors][DIAG] Found method Meowtils.addCleanMessage(String): " + m);
            } catch (NoSuchMethodException nsme) {
                System.out.println("[MeowtilsInjectors][DIAG] Meowtils.addCleanMessage(String) NOT FOUND: " + nsme);
                // Dump available methods
                for (java.lang.reflect.Method mx : clsRoot.getDeclaredMethods()) {
                    System.out.println("[MeowtilsInjectors][DIAG]   method: " + mx.toGenericString());
                }
            }
        } catch (Throwable t) {
            System.out.println("[MeowtilsInjectors][DIAG] Could not load wtf.tatp.meowtils.Meowtils: " + t);
        }
    }

    // Attempt to register injected modules into Meowtils' ModuleManager once. Returns true if registration attempted and ModuleManager was available.
    public static boolean tryRegisterModulesOnce() {
        try {
            // Resolve ModuleManager and its module list
            ClassLoader cl = InjectionMod.class.getClassLoader();
            Class<?> mmCls = Class.forName("wtf.tatp.meowtils.gui.ModuleManager", false, cl);
            java.lang.reflect.Method getModules = mmCls.getDeclaredMethod("getModules");
            Object listObj = getModules.invoke(null);
            if (!(listObj instanceof java.util.List)) {
                System.out.println("[MeowtilsInjectors][DEBUG] ModuleManager.getModules() did not return a List; got=" + (listObj == null ? "null" : listObj.getClass()));
                return false;
            }
            java.util.List modules = (java.util.List) listObj;
            int added = 0;
            synchronized (InjectionState.meowtilsInjectedModuleNames) {
                for (String display : InjectionState.meowtilsInjectedModuleNames) {
                    if (display == null || display.trim().isEmpty()) continue;
                    String cat = InjectionState.meowtilsInjectedModuleCategories.getOrDefault(display, "Utility");
                    try {
                        Object mod = com.example.runtime.ModuleInjector.createOrLoadRuntimeModule(display, display, cat, 0);
                        if (mod == null) continue;
                        // Check existing by name to avoid duplicates
                        boolean exists = false;
                        try {
                            String targetNorm = InjectionState.normalizeName(display);
                            for (Object m : modules) {
                                try {
                                    String n = String.valueOf(m.getClass().getMethod("getName").invoke(m));
                                    if (InjectionState.normalizeName(n).equals(targetNorm)) { exists = true; break; }
                                } catch (Throwable ignored) {}
                            }
                        } catch (Throwable ignored) {}
                        if (!exists) {
                            modules.add(mod);
                            added++;
                        }
                        // Track latest live instance for toggle redirection
                        try {
                            String norm = InjectionState.normalizeName(display);
                            InjectionState.latestInjectedModuleInstances.put(norm, mod);
                        } catch (Throwable ignored) {}
                    } catch (Throwable t) {
                        System.out.println("[MeowtilsInjectors][DEBUG] Failed to create/register module '" + display + "': " + t);
                    }
                }
            }
            System.out.println("[MeowtilsInjectors][DEBUG] tryRegisterModulesOnce: added=" + added + ", total now=" + modules.size());
            return true; // We were able to reach ModuleManager
        } catch (Throwable t) {
            // Likely ModuleManager not initialized yet; let caller retry
            System.out.println("[MeowtilsInjectors][DEBUG] tryRegisterModulesOnce: not ready: " + t.getClass().getSimpleName() + ": " + t.getMessage());
            return false;
        }
    }

    // Schedule a few retries in background to register modules once ModuleManager is ready
    public static void scheduleStartupRegistrationWithRetries(final int maxRetries, final int delayMs) {
        new Thread("Injector-Register-Retry") {
            @Override public void run() {
                int attempts = 0;
                while (attempts < Math.max(1, maxRetries)) {
                    attempts++;
                    try {
                        if (tryRegisterModulesOnce()) {
                            System.out.println("[MeowtilsInjectors][DEBUG] Registration attempt " + attempts + " succeeded.");
                            return;
                        }
                    } catch (Throwable ignored) {}
                    try { Thread.sleep(Math.max(50, delayMs)); } catch (InterruptedException ignored) {}
                }
                System.out.println("[MeowtilsInjectors][DEBUG] Registration retries exhausted (" + attempts + ").");
            }
        }.start();
    }
}
