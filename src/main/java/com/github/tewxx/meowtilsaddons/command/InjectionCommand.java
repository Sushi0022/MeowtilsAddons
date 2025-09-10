package com.github.tewxx.meowtilsaddons.command;

import net.minecraft.client.Minecraft;
import net.minecraft.command.CommandBase;
import net.minecraft.command.ICommandSender;
import net.minecraft.event.ClickEvent;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.ChatStyle;
import net.minecraft.util.EnumChatFormatting;
import net.minecraftforge.fml.common.Loader;
import net.minecraftforge.fml.common.ModContainer;
import com.github.tewxx.meowtilsaddons.InjectionState;

import org.benf.cfr.reader.api.CfrDriver;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Arrays;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

public class InjectionCommand extends CommandBase {

    @Override
    public String getCommandName() {
        return "injection";
    }

    // Restored for launcher compatibility (not shown in help)
    private void dumpClassLoaderInfo() {
        try {
            ClassLoader cl = net.minecraft.client.Minecraft.class.getClassLoader();
            addClientChat("CL: " + cl);
            try {
                Class<?> lcl = Class.forName("net.minecraft.launchwrapper.LaunchClassLoader");
                if (!lcl.isInstance(cl)) {
                    addClientChat("Not a LaunchClassLoader instance.");
                } else {
                    // getSources()
                    try {
                        java.lang.reflect.Method m = lcl.getDeclaredMethod("getSources");
                        m.setAccessible(true);
                        java.util.List list = (java.util.List) m.invoke(cl);
                        addClientChat("Sources: " + (list == null ? 0 : list.size()));
                        if (list != null) {
                            int shown = 0;
                            for (Object o : list) {
                                if (shown++ >= 15) { addClientChat("... (truncated)"); break; }
                                addClientChat(" - " + String.valueOf(o));
                            }
                        }
                    } catch (Throwable t) {
                        addClientChat("getSources() failed: " + t.getClass().getSimpleName() + ": " + t.getMessage());
                    }
                    // exclusions
                    try {
                        java.lang.reflect.Field f = lcl.getDeclaredField("classLoaderExceptions");
                        f.setAccessible(true);
                        java.util.Set set = (java.util.Set) f.get(cl);
                        addClientChat("classLoaderExceptions size=" + (set == null ? 0 : set.size()));
                        int shown = 0;
                        if (set != null) for (Object s : set) { if (shown++ >= 15) { addClientChat("... (truncated)"); break; } addClientChat(" - " + s); }
                    } catch (Throwable t) {
                        addClientChat("classLoaderExceptions read failed: " + t.getClass().getSimpleName());
                    }
                    try {
                        java.lang.reflect.Field f = lcl.getDeclaredField("transformerExceptions");
                        f.setAccessible(true);
                        java.util.Set set = (java.util.Set) f.get(cl);
                        addClientChat("transformerExceptions size=" + (set == null ? 0 : set.size()));
                        int shown = 0;
                        if (set != null) for (Object s : set) { if (shown++ >= 15) { addClientChat("... (truncated)"); break; } addClientChat(" - " + s); }
                    } catch (Throwable t) {
                        addClientChat("transformerExceptions read failed: " + t.getClass().getSimpleName());
                    }
                    try {
                        java.lang.reflect.Field f = lcl.getDeclaredField("negativeResourceCache");
                        f.setAccessible(true);
                        java.util.Set set = (java.util.Set) f.get(cl);
                        addClientChat("negativeResourceCache size=" + (set == null ? 0 : set.size()));
                    } catch (Throwable t) {
                        addClientChat("negativeResourceCache read failed: " + t.getClass().getSimpleName());
                    }
                }
            } catch (Throwable t) {
                addClientChat("LaunchClassLoader introspection failed: " + t.getClass().getSimpleName() + ": " + t.getMessage());
            }
        } catch (Throwable t) {
            addClientChat("clinfo failed: " + t.getClass().getSimpleName() + ": " + t.getMessage());
        }
    }

    // Restored for launcher compatibility (not shown in help)
    private void dumpAnchorCodeSources() {
        try {
            java.util.List<String> lines = new java.util.ArrayList<String>();
            try {
                Class<?> mc = Class.forName("net.minecraft.client.Minecraft", false, InjectionCommand.class.getClassLoader());
                java.net.URL u = mc.getProtectionDomain() == null || mc.getProtectionDomain().getCodeSource() == null ? null : mc.getProtectionDomain().getCodeSource().getLocation();
                lines.add("Minecraft: " + u);
            } catch (Throwable t) { lines.add("Minecraft: " + t.getClass().getSimpleName()); }
            try {
                Class<?> forge = Class.forName("net.minecraftforge.fml.common.Loader", false, InjectionCommand.class.getClassLoader());
                java.net.URL u = forge.getProtectionDomain() == null || forge.getProtectionDomain().getCodeSource() == null ? null : forge.getProtectionDomain().getCodeSource().getLocation();
                lines.add("Forge Loader: " + u);
            } catch (Throwable t) { lines.add("Forge Loader: " + t.getClass().getSimpleName()); }
            try {
                Class<?> mixin = Class.forName("org.spongepowered.asm.mixin.Mixin", false, InjectionCommand.class.getClassLoader());
                java.net.URL u = mixin.getProtectionDomain() == null || mixin.getProtectionDomain().getCodeSource() == null ? null : mixin.getProtectionDomain().getCodeSource().getLocation();
                lines.add("Sponge Mixin: " + u);
            } catch (Throwable t) { lines.add("Sponge Mixin: " + t.getClass().getSimpleName()); }
            try {
                Class<?> self = com.github.tewxx.meowtilsaddons.runtime.ModuleInjector.class;
                java.net.URL u = self.getProtectionDomain() == null || self.getProtectionDomain().getCodeSource() == null ? null : self.getProtectionDomain().getCodeSource().getLocation();
                lines.add("ModuleInjector: " + u);
            } catch (Throwable t) { lines.add("ModuleInjector: " + t.getClass().getSimpleName()); }

            // Show last javac CP dump path
            try {
                java.io.File cfgDir = Loader.instance().getConfigDir();
                java.io.File dump = new java.io.File(cfgDir, "MeowtilsInjectors/last-compiler-classpath.txt");
                lines.add("Javac CP dump: " + dump.getAbsolutePath() + (dump.exists() ? " (exists)" : " (missing)"));
            } catch (Throwable ignored) {}

            for (String s : lines) addClientChat(s);
        } catch (Throwable t) {
            addClientChat("anchors failed: " + t.getClass().getSimpleName() + ": " + t.getMessage());
        }
    }

    // Best-effort: traverse current ClickGUI (if open) and swap any fields of type Module whose getName() == name
    private void rebindModuleReferencesByName(String name, Object latestModule) {
        try {
            Object screen = net.minecraft.client.Minecraft.getMinecraft().currentScreen;
            if (screen == null) return;
            if (!screen.getClass().getName().equals("wtf.tatp.meowtils.gui.ClickGUI")) return;
        } catch (Throwable ignored) {}
        try {
            Object screen = net.minecraft.client.Minecraft.getMinecraft().currentScreen;
            java.util.Deque<Object> stack = new java.util.ArrayDeque<Object>();
            java.util.HashSet<Object> seen = new java.util.HashSet<Object>();
            stack.push(screen);
            final String targetNorm = normalizeName(name);
            int swaps = 0;
            while (!stack.isEmpty()) {
                Object obj = stack.pop();
                if (obj == null || seen.contains(obj)) continue;
                seen.add(obj);
                Class<?> c = obj.getClass();
                // Inspect fields
                for (java.lang.reflect.Field f : c.getDeclaredFields()) {
                    try {
                        f.setAccessible(true);
                        Object v = f.get(obj);
                        if (v == null) continue;
                        // If field is a Module (or subclass), swap if name matches
                        try {
                            Class<?> mCls = Class.forName("wtf.tatp.meowtils.gui.Module");
                            if (mCls.isInstance(v)) {
                                String n = String.valueOf(v.getClass().getMethod("getName").invoke(v));
                                if (normalizeName(n).equals(targetNorm)) {
                                    f.set(obj, latestModule);
                                    swaps++;
                                    continue;
                                }
                            }
                        } catch (Throwable ignored) {}
                        // If it's a collection/array, enqueue its elements
                        if (v instanceof java.util.Collection) {
                            for (Object el : (java.util.Collection) v) stack.push(el);
                        } else if (v.getClass().isArray()) {
                            int len = java.lang.reflect.Array.getLength(v);
                            for (int i = 0; i < len; i++) stack.push(java.lang.reflect.Array.get(v, i));
                        } else if (v.getClass().getName().startsWith("wtf.tatp.meowtils")) {
                            // Follow Meowtils GUI objects
                            stack.push(v);
                        }
                    } catch (Throwable ignored) {}
                }
            }
            System.out.println("[MeowtilsInjectors][DEBUG] Rebind GUI references for '" + name + "' swapped=" + swaps);
        } catch (Throwable ignored) {}
    }

    @Override
    public String getCommandUsage(ICommandSender sender) {
        return "/injection mods | /injection dump <modid> | /injection decompile <modid> | /injection injectline <message...> | /injection injectmodule <name...> <category>";
    }

    @Override
    public int getRequiredPermissionLevel() {
        // Allow all players (client-side command)
        return 0;
    }

    @Override
    public List<String> getCommandAliases() {
        // Optional short alias
        return Collections.singletonList("inj");
    }

    @Override
    public void processCommand(ICommandSender sender, String[] args) {
        if (args.length == 1 && "mods".equalsIgnoreCase(args[0])) {
            sendActiveMods();
            return;
        }
        if (args.length >= 1 && "folder".equalsIgnoreCase(args[0])) {
            // Open the MeowtilsInjectors config folder in the OS file manager
            try {
                java.io.File cfgDir = Loader.instance().getConfigDir();
                java.io.File meowCfgDir = new java.io.File(cfgDir, "MeowtilsInjectors");
                if (!meowCfgDir.exists()) meowCfgDir.mkdirs();
                openInFileManager(meowCfgDir.getAbsolutePath());
            } catch (Throwable t) {
                addClientChat("Failed to open folder: " + t.getClass().getSimpleName() + ": " + t.getMessage());
            }
            return;
        }
        if (args.length >= 1 && "clinfo".equalsIgnoreCase(args[0])) {
            dumpClassLoaderInfo();
            return;
        }
        if (args.length >= 1 && "anchors".equalsIgnoreCase(args[0])) {
            dumpAnchorCodeSources();
            return;
        }
        if (args.length >= 2 && "openfile".equalsIgnoreCase(args[0])) {
            // Usage: /injection openfile <absolute path with spaces allowed>
            StringBuilder sb = new StringBuilder();
            for (int i = 1; i < args.length; i++) {
                if (i > 1) sb.append(' ');
                sb.append(args[i]);
            }
            String path = sb.toString();
            openInFileManager(path);
            return;
        }
        if (args.length >= 1 && "registermodules".equalsIgnoreCase(args[0])) {
            boolean ok = com.github.tewxx.meowtilsaddons.InjectionMod.tryRegisterModulesOnce();
            if (ok) {
                addClientChat("Tried to register modules. If you don't see them, try /injection refreshgui.");
            } else {
                addClientChat("ModuleManager not ready; will retry in background.");
                com.github.tewxx.meowtilsaddons.InjectionMod.scheduleStartupRegistrationWithRetries(3, 1000);
            }
            return;
        }
        if (args.length >= 1 && "listmm".equalsIgnoreCase(args[0])) {
            // Debug: list ModuleManager entries
            try {
                Class<?> mm = Class.forName("wtf.tatp.meowtils.gui.ModuleManager");
                java.lang.reflect.Method gm = mm.getDeclaredMethod("getModules");
                java.util.List list = (java.util.List) gm.invoke(null);
                if (list == null) { addClientChat("ModuleManager.getModules() is null"); return; }
                addClientChat("ModuleManager size: " + list.size());
                int shown = 0;
                for (Object m : list) {
                    if (shown++ >= 20) { addClientChat("... (truncated)"); break; }
                    try {
                        String n = String.valueOf(m.getClass().getMethod("getName").invoke(m));
                        addClientChat(" - " + n + " (" + m.getClass().getName() + ")");
                    } catch (Throwable ignored) {}
                }
            } catch (Throwable t) {
                addClientChat("listmm failed: " + t.getClass().getSimpleName() + ": " + t.getMessage());
            }
            return;
        }
        if (args.length == 2 && "dump".equalsIgnoreCase(args[0])) {
            dumpModClasses(args[1]);
            return;
        }
        if (args.length == 2 && "decompile".equalsIgnoreCase(args[0])) {
            decompileMod(args[1]);
            return;
        }
        if (args.length >= 1 && "injectline".equalsIgnoreCase(args[0])) {
            handleInjectLine(sender, args);
            return;
        }
        if (args.length >= 3 && "injectmodule".equalsIgnoreCase(args[0])) {
            handleInjectModule(sender, args);
            return;
        }
        if (args.length >= 2 && "reloadmodule".equalsIgnoreCase(args[0])) {
            handleReloadModule(sender, args);
            return;
        }
        if (args.length >= 1 && "refreshgui".equalsIgnoreCase(args[0])) {
            handleRefreshGui();
            return;
        }
        // Multi-line help for clarity
        addClientChat("Commands:");
        addClientChatRaw("&7 /injection mods - List active mod IDs");
        addClientChatRaw(" ");
        addClientChatRaw("&7 /injection dump <modid> - Dump .class files of a mod to Downloads/injection-dumps");
        addClientChatRaw(" ");
        addClientChatRaw("&7 /injection decompile <modid> - Decompile a mod to Downloads/injection-dumps-decompiled");
        addClientChatRaw(" ");
        addClientChatRaw("&7 /injection injectline <message...> - Inject a line into /meowtils output");
        addClientChatRaw(" ");
        addClientChatRaw("&7 /injection injectmodule <name...> <category> - Create an injected module and save editable source");
        addClientChatRaw(" ");
        addClientChatRaw("&7 /injection reloadmodule <name...> - Recompile and hot-reload an injected module from source");
        addClientChatRaw(" ");
        addClientChatRaw("&7 /injection folder - Open the MeowtilsInjectors config folder");
    }

    private void sendActiveMods() {
        List<ModContainer> mods = Loader.instance().getActiveModList();
        List<String> ids = new ArrayList<String>();
        for (ModContainer mod : mods) {
            ids.add(mod.getModId());
        }
        Collections.sort(ids, String.CASE_INSENSITIVE_ORDER);

        if (ids.isEmpty()) {
            addClientChat("No active mods found.");
            return;
        }

        addClientChat("Active mod IDs (" + ids.size() + "): ");

        // Print in chunks to avoid overly long lines
        StringBuilder line = new StringBuilder();
        for (int i = 0; i < ids.size(); i++) {
            String id = ids.get(i);
            if (line.length() + id.length() + 2 > 200) {
                addClientChat(line.toString());
                line.setLength(0);
            }
            if (line.length() > 0) line.append(", ");
            line.append(id);
        }
        if (line.length() > 0) {
            addClientChat(line.toString());
        }
    }

    private void dumpModClasses(String modid) {
        try {
            Map<String, ModContainer> index = Loader.instance().getIndexedModList();
            ModContainer target = index.get(modid);
            if (target == null) {
                addClientChat("Unknown modid: '" + modid + "'. Use /injection mods to list IDs.");
                return;
            }
            File source = target.getSource();
            if (source == null || !source.exists()) {
                addClientChat("Could not locate source for mod '" + modid + "'.");
                return;
            }
            // Dump to user's Downloads/injection-dumps
            File downloads = new File(System.getProperty("user.home"), "Downloads");
            if (!downloads.exists()) downloads.mkdirs();
            File baseDir = new File(downloads, "injection-dumps");
            if (!baseDir.exists() && !baseDir.mkdirs()) {
                addClientChat("Failed to create base dump directory: " + baseDir.getAbsolutePath());
                return;
            }
            File outDir = new File(baseDir, modid + "-" + System.currentTimeMillis());
            if (!outDir.mkdirs()) {
                addClientChat("Failed to create dump directory: " + outDir.getAbsolutePath());
                return;
            }

            int count;
            if (source.isFile()) {
                count = extractClassesFromJar(source, outDir);
            } else {
                count = copyClassesFromDirectory(source.toPath(), outDir.toPath());
            }

            // Send clickable message to open folder
            ChatComponentText base = new ChatComponentText(getChatPrefix() + "Dumped " + count + " class files to: " + outDir.getAbsolutePath() + " ");
            ChatComponentText open = new ChatComponentText("[Open Folder]");
            open.setChatStyle(new ChatStyle().setColor(EnumChatFormatting.GRAY).setChatClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/injection openfile " + outDir.getAbsolutePath())));
            base.appendSibling(open);
            if (Minecraft.getMinecraft() != null && Minecraft.getMinecraft().thePlayer != null) {
                Minecraft.getMinecraft().thePlayer.addChatMessage(base);
            } else {
                addClientChat("Dumped " + count + " class files to: " + outDir.getAbsolutePath());
            }
        } catch (Throwable t) {
            addClientChat("Dump failed: " + t.getClass().getSimpleName() + ": " + t.getMessage());
        }
    }

    private int extractClassesFromJar(File jarFile, File outDir) throws IOException {
        int count = 0;
        ZipFile zip = new ZipFile(jarFile);
        try {
            Enumeration<? extends ZipEntry> entries = zip.entries();
            while (entries.hasMoreElements()) {
                ZipEntry e = entries.nextElement();
                if (e.isDirectory()) continue;
                String name = e.getName();
                if (!name.endsWith(".class")) continue;
                File out = new File(outDir, name);
                File parent = out.getParentFile();
                if (!parent.exists() && !parent.mkdirs()) continue;
                try (InputStream is = zip.getInputStream(e); FileOutputStream fos = new FileOutputStream(out)) {
                    byte[] buf = new byte[8192];
                    int r;
                    while ((r = is.read(buf)) != -1) {
                        fos.write(buf, 0, r);
                    }
                    count++;
                }
            }
        } finally {
            zip.close();
        }
        return count;
    }

    private int copyClassesFromDirectory(Path sourceDir, Path outDir) throws IOException {
        final int[] count = {0};
        Files.walk(sourceDir)
                .filter(p -> Files.isRegularFile(p) && p.toString().endsWith(".class"))
                .forEach(p -> {
                    try {
                        Path rel = sourceDir.relativize(p);
                        Path dest = outDir.resolve(rel);
                        Files.createDirectories(dest.getParent());
                        Files.copy(p, dest);
                        count[0]++;
                    } catch (IOException ignored) {
                    }
                });
        return count[0];
    }

    private void decompileMod(String modid) {
        try {
            Map<String, ModContainer> index = Loader.instance().getIndexedModList();
            ModContainer target = index.get(modid);
            if (target == null) {
                addClientChat("Unknown modid: '" + modid + "'. Use /injection mods to list IDs.");
                return;
            }
            File source = target.getSource();
            if (source == null || !source.exists()) {
                addClientChat("Could not locate source for mod '" + modid + "'.");
                return;
            }
            // Output to Downloads/injection-dumps-decompiled
            File downloads = new File(System.getProperty("user.home"), "Downloads");
            if (!downloads.exists()) downloads.mkdirs();
            File outDir = new File(new File(downloads, "injection-dumps-decompiled"), modid + "-" + System.currentTimeMillis());
            if (!outDir.mkdirs()) {
                addClientChat("Failed to create decompile directory: " + outDir.getAbsolutePath());
                return;
            }

            HashMap<String, String> options = new HashMap<String, String>();
            options.put("outputdir", outDir.getAbsolutePath());
            options.put("silent", "true");
            CfrDriver driver = new CfrDriver.Builder().withOptions(options).build();

            if (source.isFile()) {
                driver.analyse(Arrays.asList(source.getAbsolutePath()));
            } else {
                // Directory of classes: decompile each class file (CFR will keep package structure)
                List<String> files = new ArrayList<String>();
                Files.walk(source.toPath())
                        .filter(p -> Files.isRegularFile(p) && p.toString().endsWith(".class"))
                        .forEach(p -> files.add(p.toAbsolutePath().toString()));
                if (files.isEmpty()) {
                    addClientChat("No class files found in '" + source.getAbsolutePath() + "'.");
                    return;
                }
                driver.analyse(files);
            }

            ChatComponentText base = new ChatComponentText(getChatPrefix() + "Decompiled to: " + outDir.getAbsolutePath() + " ");
            ChatComponentText open = new ChatComponentText("[Open Folder]");
            open.setChatStyle(new ChatStyle().setColor(EnumChatFormatting.GRAY).setChatClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/injection openfile " + outDir.getAbsolutePath())));
            base.appendSibling(open);
            if (Minecraft.getMinecraft() != null && Minecraft.getMinecraft().thePlayer != null) {
                Minecraft.getMinecraft().thePlayer.addChatMessage(base);
            } else {
                addClientChat("Decompiled to: " + outDir.getAbsolutePath());
            }
        } catch (Throwable t) {
            addClientChat("Decompile failed: " + t.getClass().getSimpleName() + ": " + t.getMessage());
        }
    }

    private static final String CHAT_PREFIX_RAW = "[&dInjector&f] ";

    private String getChatPrefix() {
        return translateLegacyColors(CHAT_PREFIX_RAW);
    }

    private void addClientChat(String message) {
        if (Minecraft.getMinecraft() != null && Minecraft.getMinecraft().thePlayer != null) {
            String formatted = getChatPrefix() + translateLegacyColors(message == null ? "" : message);
            Minecraft.getMinecraft().thePlayer.addChatMessage(new ChatComponentText(formatted));
        }
    }

    // Normalize a module display name for comparisons: strip color codes (§x and &x), trim, lowercase
    private static String normalizeName(String s) {
        if (s == null) return "";
        try {
            String noColors = s.replaceAll("\u00A7.", "").replaceAll("&.", "");
            return noColors.trim().toLowerCase(java.util.Locale.ROOT);
        } catch (Throwable ignored) {
            return s.trim().toLowerCase(java.util.Locale.ROOT);
        }
    }

    private void handleInjectLine(ICommandSender sender, String[] args) {
        if (args.length < 2) {
            addClientChat("No message provided. Try: /injection injectline <your message>");
            return;
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 1; i < args.length; i++) {
            if (i > 1) sb.append(' ');
            sb.append(args[i]);
        }
        String msg = sb.toString().trim();
        if (msg.isEmpty()) {
            addClientChat("Your message was empty. Please provide some text to inject.");
            return;
        }
        // Support legacy color codes using & by converting to §
        msg = translateLegacyColors(msg);
        InjectionState.meowtilsInjectMessages.add(msg);
        InjectionState.meowtilsInjectEnabled = true;
        addClientChat("Added runtime injection line (multi-line supported). It will be injected after '/autotext<1-10> clear' in Meowtils /meow output.");
    }

    private void handleInjectModule(ICommandSender sender, String[] args) {
        // /injection injectmodule <name...> <category>
        if (args.length < 3) {
            addClientChat("Usage: /injection injectmodule <name...> <category>");
            return;
        }
        // Category is the last token; name may contain spaces in between
        String categoryName = args[args.length - 1];
        StringBuilder sb = new StringBuilder();
        for (int i = 1; i < args.length - 1; i++) {
            if (i > 1) sb.append(' ');
            sb.append(args[i]);
        }
        String name = sb.toString().trim();
        if (name.isEmpty()) {
            addClientChat("Module name cannot be empty.");
            return;
        }
        if (categoryName == null || categoryName.trim().isEmpty()) {
            addClientChat("Category cannot be empty. Example categories: Utility, Hypixel");
            return;
        }
        name = translateLegacyColors(name);
        synchronized (InjectionState.meowtilsInjectedModuleNames) {
            if (!InjectionState.meowtilsInjectedModuleNames.contains(name)) {
                InjectionState.meowtilsInjectedModuleNames.add(0, name);
            }
        }
        // Track category for this name
        com.github.tewxx.meowtilsaddons.InjectionState.meowtilsInjectedModuleCategories.put(name, categoryName);
        // Generate editable source file for the injected module under config/MeowtilsInjectors/modules-src
        try {
            String simple = sanitizeSimpleNameForSource(name);
            net.minecraftforge.fml.common.Loader loader = net.minecraftforge.fml.common.Loader.instance();
            java.io.File cfgDir = loader.getConfigDir();
            java.io.File srcDir = new java.io.File(cfgDir, "MeowtilsInjectors/modules-src");
            if (!srcDir.exists()) srcDir.mkdirs();
            java.io.File srcFile = new java.io.File(srcDir, simple + ".java");
            String pkgCat = categoryName == null ? "utility" : categoryName.trim().toLowerCase(java.util.Locale.ROOT);
            String pkg = "wtf.tatp.meowtils.modules." + pkgCat;
            String className = simple;
            String contents =
                "package " + pkg + ";\n\n" +
                "import wtf.tatp.meowtils.gui.Module;\n\n" +
                "public class " + className + " extends Module {\n\n" +
                "    public " + className + "() {\n" +
                "        super(\"" + name.replace("\"","\\\"") + "\", \"\", \"\", Category." + capitalizeEnum(categoryName) + ");\n" +
                "        this.tooltip(\"Injected module.\");\n" +
                "    }\n\n" +
                "}\n";
            try {
                java.nio.file.Files.write(srcFile.toPath(), contents.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            } catch (Throwable ignoredWrite) {}
        } catch (Throwable ignoredGen) {}

        // Add enum constant to Module.Modules at runtime (best-effort)
        try {
            com.github.tewxx.meowtilsaddons.runtime.EnumInjector.addModuleEnumConstant(name);
        } catch (Throwable ignored) {}
        // Immediately create and add the module instance to ModuleManager list
        try {
            Object module = com.github.tewxx.meowtilsaddons.runtime.ModuleInjector.createOrLoadRuntimeModule(name, name, categoryName, 0);
            Class<?> mm = Class.forName("wtf.tatp.meowtils.gui.ModuleManager");
            java.lang.reflect.Method gm = mm.getDeclaredMethod("getModules");
            java.util.List list = (java.util.List) gm.invoke(null);
            // Ensure not duplicated by name
            boolean exists = false;
            int before = list == null ? -1 : list.size();
            if (list != null) {
                for (Object m : list) {
                    try {
                        java.lang.reflect.Method getName = m.getClass().getMethod("getName");
                        String n = String.valueOf(getName.invoke(m));
                        if (name.equals(n)) { exists = true; break; }
                    } catch (Throwable ignored) {}
                }
            }
            if (exists) {
                // Remove any existing entries with the same display name (e.g., placeholder from earlier attempts)
                try {
                    java.util.Iterator it = list.iterator();
                    while (it.hasNext()) {
                        Object m = it.next();
                        try {
                            java.lang.reflect.Method getName = m.getClass().getMethod("getName");
                            String n = String.valueOf(getName.invoke(m));
                            if (name.equals(n)) { it.remove(); }
                        } catch (Throwable ignored) {}
                    }
                } catch (Throwable ignored) {}
            }
            if (module != null) {
                list.add(0, module);
                int after = list.size();
                try {
                    com.github.tewxx.meowtilsaddons.InjectionState.latestInjectedModuleInstances.put(normalizeName(name), module);
                } catch (Throwable ignored) {}
                // Dump a few module names for verification
                try {
                    int dump = Math.min(after, 5);
                    StringBuilder sbDump = new StringBuilder("Top modules now: ");
                    for (int i = 0; i < dump; i++) {
                        Object m = list.get(i);
                        try {
                            java.lang.reflect.Method getName = m.getClass().getMethod("getName");
                            sbDump.append('[').append(getName.invoke(m)).append(']').append(' ');
                        } catch (Throwable ignored) {}
                    }
                    System.out.println("[MeowtilsInjectors][DEBUG] " + sbDump.toString());
                } catch (Throwable ignored) {}
                // Persist to our config so it's also registered on next launch (name|category)
                try {
                    net.minecraftforge.fml.common.Loader loader = net.minecraftforge.fml.common.Loader.instance();
                    java.io.File cfgDir = loader.getConfigDir();
                    java.io.File meowCfgDir = new java.io.File(cfgDir, "MeowtilsInjectors");
                    if (!meowCfgDir.exists()) meowCfgDir.mkdirs();
                    java.io.File modulesTxt = new java.io.File(meowCfgDir, "modules.txt");
                    java.util.List<String> lines = new java.util.ArrayList<String>();
                    if (modulesTxt.exists()) {
                        try {
                            lines = java.nio.file.Files.readAllLines(modulesTxt.toPath(), java.nio.charset.StandardCharsets.UTF_8);
                        } catch (Throwable ignored2) {}
                    }
                    String entry = name + "|" + categoryName;
                    if (!lines.contains(entry)) {
                        java.nio.file.Files.write(modulesTxt.toPath(), (entry + System.lineSeparator()).getBytes(java.nio.charset.StandardCharsets.UTF_8), java.nio.file.StandardOpenOption.CREATE, java.nio.file.StandardOpenOption.APPEND);
                    }
                } catch (Throwable t) {
                    System.out.println("[MeowtilsInjectors][DEBUG] Failed to persist injected module to config: " + t);
                }
                // Inform user to restart and provide clickable [Show in Explorer] to the editable source
                try {
                    // Compute path to generated source file
                    String simple = sanitizeSimpleNameForSource(name);
                    net.minecraftforge.fml.common.Loader loader = net.minecraftforge.fml.common.Loader.instance();
                    java.io.File cfgDir = loader.getConfigDir();
                    java.io.File srcDir = new java.io.File(cfgDir, "MeowtilsInjectors/modules-src");
                    java.io.File srcFile = new java.io.File(srcDir, simple + ".java");

                    ChatComponentText base = new ChatComponentText(getChatPrefix() + "Injected module saved: '" + name + "' in category '" + categoryName + "'. Please restart your game for it to take effect. ");
                    ChatComponentText open = new ChatComponentText("[Show in Explorer]");
                    open.setChatStyle(new ChatStyle().setColor(EnumChatFormatting.GRAY).setChatClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/injection openfile " + srcFile.getAbsolutePath())));
                    base.appendSibling(open);
                    if (Minecraft.getMinecraft() != null && Minecraft.getMinecraft().thePlayer != null) {
                        Minecraft.getMinecraft().thePlayer.addChatMessage(base);
                    } else {
                        addClientChat("Source: " + srcFile.getAbsolutePath());
                    }
                } catch (Throwable ignored) {
                    addClientChat("Injected module saved: '" + name + "' in category '" + categoryName + "'. Please restart your game for it to take effect.");
                }
            } else {
                addClientChat("Module already present or failed to create: '" + name + "'. Try reopening the GUI.");
            }
        } catch (Throwable t) {
            addClientChat("Injected module queued: '" + name + "'. Reopen the Meowtils GUI to refresh.");
        }
    }

    private void handleRefreshGui() {
        try {
            // Try to open a new ClickGUI instance via reflection
            Class<?> mcCls = Class.forName("net.minecraft.client.Minecraft");
            Object mc = mcCls.getMethod("getMinecraft").invoke(null);
            Class<?> clickGuiCls = Class.forName("wtf.tatp.meowtils.gui.ClickGUI");
            Object gui = clickGuiCls.getDeclaredConstructor().newInstance();
            mcCls.getMethod("displayGuiScreen", Class.forName("net.minecraft.client.gui.GuiScreen")).invoke(mc, gui);
            addClientChat("Requested GUI refresh (opened new ClickGUI). If it doesn't appear, reopen manually.");
        } catch (Throwable t) {
            addClientChat("Could not auto-open ClickGUI. Reopen it manually.");
        }
    }

    // /injection reloadmodule <name...>
    private void handleReloadModule(ICommandSender sender, String[] args) {
        if (args.length < 2) {
            addClientChat("Usage: /injection reloadmodule <name...>");
            return;
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 1; i < args.length; i++) {
            if (i > 1) sb.append(' ');
            sb.append(args[i]);
        }
        String name = translateLegacyColors(sb.toString().trim());
        if (name.isEmpty()) {
            addClientChat("Module name cannot be empty.");
            return;
        }
        try {
            // Locate existing instance and remember its state
            Class<?> mm = Class.forName("wtf.tatp.meowtils.gui.ModuleManager");
            java.lang.reflect.Method gm = mm.getDeclaredMethod("getModules");
            java.util.List list = (java.util.List) gm.invoke(null);
            if (list == null) { addClientChat("Module list unavailable."); return; }

            int idx = -1; Object old = null; boolean enabled = false; String category = com.github.tewxx.meowtilsaddons.InjectionState.meowtilsInjectedModuleCategories.get(name);
            for (int i = 0; i < list.size(); i++) {
                Object m = list.get(i);
                try {
                    java.lang.reflect.Method getName = m.getClass().getMethod("getName");
                    String n = String.valueOf(getName.invoke(m));
                    if (name.equals(n)) {
                        idx = i; old = m;
                        // Try getState()/isToggled()
                        try {
                            java.lang.reflect.Method getState = m.getClass().getMethod("getState");
                            enabled = ((Boolean) getState.invoke(m)).booleanValue();
                        } catch (Throwable t1) {
                            try {
                                java.lang.reflect.Method isToggled = m.getClass().getMethod("isToggled");
                                enabled = ((Boolean) isToggled.invoke(m)).booleanValue();
                            } catch (Throwable ignored) {}
                        }
                        break;
                    }
                } catch (Throwable ignored) {}
            }

            if (idx == -1) {
                addClientChat("Module not found in list: '" + name + "'. Make sure it is injected and visible in the GUI.");
                return;
            }

            if (category == null || category.trim().isEmpty()) category = "Utility";
            // Recreate module; ModuleInjector will prioritize compiled source if present
            Object module = com.github.tewxx.meowtilsaddons.runtime.ModuleInjector.createOrLoadRuntimeModule(name, name, category, 0);
            if (module == null) {
                addClientChat("Reload failed to create module: '" + name + "'. Check your source compiles.");
                return;
            }

            // Debug: log old instance class and loader
            try {
                System.out.println("[MeowtilsInjectors][DEBUG] Reload old instance class=" + old.getClass() + ", loader=" + old.getClass().getClassLoader());
            } catch (Throwable ignored) {}

            // Unregister the old instance from event buses to avoid lingering behavior
            try {
                // MinecraftForge.EVENT_BUS.unregister(old)
                Class<?> mf = Class.forName("net.minecraftforge.common.MinecraftForge");
                Object eventBus = mf.getField("EVENT_BUS").get(null);
                eventBus.getClass().getMethod("unregister", Object.class).invoke(eventBus, old);
            } catch (Throwable ignored) {}
            try {
                // FMLCommonHandler.instance().bus().unregister(old)
                Class<?> fml = Class.forName("net.minecraftforge.fml.common.FMLCommonHandler");
                Object instFml = fml.getMethod("instance").invoke(null);
                Object bus = instFml.getClass().getMethod("bus").invoke(instFml);
                bus.getClass().getMethod("unregister", Object.class).invoke(bus, old);
            } catch (Throwable ignored) {}

            // Remove any duplicate entries with the same normalized display name or same FQCN (except our replacement slot)
            int removed = 0;
            String targetFqcn = null;
            try { targetFqcn = old.getClass().getName(); } catch (Throwable ignored) {}
            final String normTarget = normalizeName(name);
            try {
                java.util.Iterator it = list.iterator();
                int i = 0;
                while (it.hasNext()) {
                    Object m = it.next();
                    boolean isReplacementSlot = (i == idx);
                    String className = null;
                    try { className = m.getClass().getName(); } catch (Throwable ignored) {}
                    String n = null;
                    try {
                        java.lang.reflect.Method getName = m.getClass().getMethod("getName");
                        n = String.valueOf(getName.invoke(m));
                    } catch (Throwable ignored) {}
                    String norm = n == null ? null : normalizeName(n);
                    boolean sameName = norm != null && norm.equals(normTarget);
                    boolean sameFqcn = targetFqcn != null && targetFqcn.equals(className);
                    if (!isReplacementSlot && (sameName || sameFqcn)) {
                        // Unregister stale instance too
                        try {
                            Class<?> mf2 = Class.forName("net.minecraftforge.common.MinecraftForge");
                            Object eb2 = mf2.getField("EVENT_BUS").get(null);
                            eb2.getClass().getMethod("unregister", Object.class).invoke(eb2, m);
                        } catch (Throwable ignored) {}
                        try {
                            Class<?> fml2 = Class.forName("net.minecraftforge.fml.common.FMLCommonHandler");
                            Object inst2 = fml2.getMethod("instance").invoke(null);
                            Object bus2 = inst2.getClass().getMethod("bus").invoke(inst2);
                            bus2.getClass().getMethod("unregister", Object.class).invoke(bus2, m);
                        } catch (Throwable ignored) {}
                        it.remove();
                        removed++;
                    }
                    i++;
                }
            } catch (Throwable ignored) {}

            // Replace in list at the same index (or append if idx now out of bounds)
            if (idx >= 0 && idx < list.size()) {
                list.set(idx, module);
            } else {
                list.add(0, module);
            }
            try {
                com.github.tewxx.meowtilsaddons.InjectionState.latestInjectedModuleInstances.put(normalizeName(name), module);
            } catch (Throwable ignored) {}

            // Debug: log new instance class and loader
            try {
                System.out.println("[MeowtilsInjectors][DEBUG] Reload new instance class=" + module.getClass() + ", loader=" + module.getClass().getClassLoader() + ", removedDuplicates=" + removed);
            } catch (Throwable ignored) {}

            // Restore state
            try {
                java.lang.reflect.Method setState = module.getClass().getMethod("setState", boolean.class);
                setState.invoke(module, enabled);
            } catch (Throwable ignored) {}

            // Auto-refresh GUI to drop cached references to old instance
            try {
                handleRefreshGui();
                System.out.println("[MeowtilsInjectors][DEBUG] Refreshed GUI after reload for '" + name + "'");
            } catch (Throwable ignored) {}

            // Aggressively rebind any GUI/internal Module references (best-effort)
            try {
                Object latest = com.github.tewxx.meowtilsaddons.InjectionState.latestInjectedModuleInstances.get(normalizeName(name));
                if (latest != null) {
                    // Replace in ModuleManager lists by name
                    try {
                        Class<?> mm2 = Class.forName("wtf.tatp.meowtils.gui.ModuleManager");
                        java.lang.reflect.Method gm2 = mm2.getDeclaredMethod("getModules");
                        java.util.List list2 = (java.util.List) gm2.invoke(null);
                        if (list2 != null) {
                            for (int i2 = 0; i2 < list2.size(); i2++) {
                                Object m2 = list2.get(i2);
                                try {
                                    String n2 = String.valueOf(m2.getClass().getMethod("getName").invoke(m2));
                                    if (normalizeName(n2).equals(normalizeName(name))) list2.set(i2, latest);
                                } catch (Throwable ignored) {}
                            }
                        }
                    } catch (Throwable ignored) {}
                    // Replace in per-category lists returned by Module.getCategoryModules(Category)
                    try {
                        Class<?> modCls = Class.forName("wtf.tatp.meowtils.gui.Module");
                        Class<?> catCls = Class.forName("wtf.tatp.meowtils.gui.Module$Category");
                        java.lang.reflect.Method gcm = modCls.getDeclaredMethod("getCategoryModules", catCls);
                        Object[] cats = catCls.getEnumConstants();
                        if (cats != null) {
                            for (Object cat : cats) {
                                try {
                                    java.util.List catList = (java.util.List) gcm.invoke(null, cat);
                                    if (catList == null) continue;
                                    for (int j = 0; j < catList.size(); j++) {
                                        Object m3 = catList.get(j);
                                        try {
                                            String n3 = String.valueOf(m3.getClass().getMethod("getName").invoke(m3));
                                            if (normalizeName(n3).equals(normalizeName(name))) catList.set(j, latest);
                                        } catch (Throwable ignored) {}
                                    }
                                } catch (Throwable ignored) {}
                            }
                        }
                    } catch (Throwable ignored) {}
                    rebindModuleReferencesByName(name, latest);
                }
            } catch (Throwable t) {
                System.out.println("[MeowtilsInjectors][DEBUG] Rebind module references failed: " + t);
            }

            // Provide clickable [Show in Explorer] to source
            try {
                String simple = sanitizeSimpleNameForSource(name);
                net.minecraftforge.fml.common.Loader loader = net.minecraftforge.fml.common.Loader.instance();
                java.io.File cfgDir = loader.getConfigDir();
                java.io.File srcDir = new java.io.File(cfgDir, "MeowtilsInjectors/modules-src");
                java.io.File srcFile = new java.io.File(srcDir, simple + ".java");
                ChatComponentText base = new ChatComponentText(getChatPrefix() + "Reloaded module: '" + name + "' ");
                ChatComponentText open = new ChatComponentText("[Show in Explorer]");
                open.setChatStyle(new ChatStyle().setColor(EnumChatFormatting.GRAY).setChatClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/injection openfile " + srcFile.getAbsolutePath())));
                base.appendSibling(open);
                if (Minecraft.getMinecraft() != null && Minecraft.getMinecraft().thePlayer != null) {
                    Minecraft.getMinecraft().thePlayer.addChatMessage(base);
                } else {
                    addClientChat("Reloaded '" + name + "'. Source: " + srcFile.getAbsolutePath());
                }
            } catch (Throwable ignored) {
                addClientChat("Reloaded module: '" + name + "'");
            }
        } catch (Throwable t) {
            addClientChat("Reload failed: " + t.getClass().getSimpleName() + ": " + t.getMessage());
        }
    }

    // Converts legacy color codes like &a, &l to the Minecraft section sign (§a, §l)
    private String translateLegacyColors(String input) {
        if (input == null || input.indexOf('&') == -1) return input;
        StringBuilder out = new StringBuilder(input.length());
        for (int i = 0; i < input.length(); i++) {
            char c = input.charAt(i);
            if (c == '&' && i + 1 < input.length()) {
                char n = input.charAt(i + 1);
                // valid codes 0-9 a-f k-o r (case-insensitive)
                if ((n >= '0' && n <= '9') ||
                        (n >= 'a' && n <= 'f') || (n >= 'A' && n <= 'F') ||
                        (n >= 'k' && n <= 'o') || (n >= 'K' && n <= 'O') ||
                        n == 'r' || n == 'R') {
                    out.append('\u00A7');
                    out.append(Character.toLowerCase(n));
                    i++; // skip next
                    continue;
                }
            }
            out.append(c);
        }
        return out.toString();
    }

    @Override
    public boolean canCommandSenderUseCommand(ICommandSender sender) {
        // Always true for client command
        return true;
    }

    // Sends raw message without the Injector prefix, with legacy color codes supported
    private void addClientChatRaw(String message) {
        if (Minecraft.getMinecraft() != null && Minecraft.getMinecraft().thePlayer != null) {
            String formatted = translateLegacyColors(message == null ? "" : message);
            Minecraft.getMinecraft().thePlayer.addChatMessage(new ChatComponentText(formatted));
        }
    }

    // Local sanitizer for building source file names that match ModuleInjector
    private String sanitizeSimpleNameForSource(String in) {
        if (in == null || in.isEmpty()) return "Injected";
        String s = in.replaceAll("\u00A7.", "").replaceAll("&.", "");
        s = s.replaceAll("[^A-Za-z0-9_]", "");
        if (s.isEmpty() || !Character.isJavaIdentifierStart(s.charAt(0))) s = "Injected" + s;
        return s;
    }

    private String capitalizeEnum(String cat) {
        if (cat == null) return "Utility";
        String c = cat.trim();
        if (c.isEmpty()) return "Utility";
        // Known categories in Meowtils enum are capitalized exact
        String u = c.toLowerCase(java.util.Locale.ROOT);
        if (u.equals("meowtils")) return "Meowtils";
        if (u.equals("hypixel")) return "Hypixel";
        if (u.equals("skywars")) return "Skywars";
        if (u.equals("bedwars")) return "Bedwars";
        if (u.equals("render")) return "Render";
        if (u.equals("antisnipe")) return "Antisnipe";
        if (u.equals("utility")) return "Utility";
        if (u.equals("advanced")) return "Advanced";
        return "Utility";
    }

    // Opens the given path in the OS file manager. On Windows, prefers Explorer with selection.
    private void openInFileManager(String path) {
        if (path == null || path.trim().isEmpty()) {
            addClientChat("Path missing.");
            return;
        }
        try {
            java.io.File f = new java.io.File(path);
            if (!f.exists()) {
                addClientChat("Path not found: " + path);
                return;
            }
            String os = System.getProperty("os.name", "").toLowerCase(java.util.Locale.ROOT);
            if (os.contains("win")) {
                java.util.List<String> cmd = new java.util.ArrayList<String>();
                cmd.add("explorer");
                if (f.isFile()) {
                    // /select,<path> must be a single argument
                    cmd.add("/select," + f.getAbsolutePath());
                } else {
                    cmd.add(f.getAbsolutePath());
                }
                new ProcessBuilder(cmd).start();
            } else if (os.contains("mac")) {
                if (f.isFile()) {
                    new ProcessBuilder("open", "-R", f.getAbsolutePath()).start();
                } else {
                    new ProcessBuilder("open", f.getAbsolutePath()).start();
                }
            } else {
                java.io.File target = f.isFile() ? f.getParentFile() : f;
                if (target == null) target = f;
                new ProcessBuilder("xdg-open", target.getAbsolutePath()).start();
            }
        } catch (Throwable t) {
            addClientChat("Failed to open in file manager: " + t.getClass().getSimpleName() + ": " + t.getMessage());
        }
    }
}
