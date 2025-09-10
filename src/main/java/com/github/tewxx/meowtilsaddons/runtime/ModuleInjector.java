package com.example.runtime;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.security.ProtectionDomain;
import java.util.Arrays;
import java.util.Locale;
import java.util.regex.Pattern;
import javax.tools.Diagnostic;
import javax.tools.DiagnosticCollector;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.ToolProvider;

/**
 * Runtime generator/loader for Meowtils modules.
 *
 * Attempts to create a real class under wtf.tatp.meowtils.modules with the exact
 * simple name provided. If generation fails (e.g., ASM unavailable), falls back to
 * constructing a GUI placeholder and renaming it for visual purposes.
 */
public final class ModuleInjector {
    private ModuleInjector() {}

    public static Object createOrLoadRuntimeModule(String classSimpleName, String displayName, String categoryName, int key) {
        try {
            String sanitizedSimple = sanitizeSimpleName(classSimpleName);
            String targetName = "wtf.tatp.meowtils.modules." + sanitizedSimple;
            ClassLoader cl = ModuleInjector.class.getClassLoader();
            System.out.println("[MeowtilsInjectors][DEBUG] ModuleInjector: requested create class='" + classSimpleName + "' | sanitized='" + sanitizedSimple + "' | fqcn='" + targetName + "' | display='" + displayName + "' | category='" + categoryName + "' | key=" + key);

            // Ensure a source template exists for user editing
            tryWriteSourceTemplate(sanitizedSimple, displayName, categoryName);

            // If an editable source exists, attempt to compile and load it first
            Object compiled = tryCompileAndLoadFromSource(targetName);
            if (compiled != null) {
                System.out.println("[MeowtilsInjectors][DEBUG] Loaded module from compiled source for " + targetName);
                return compiled;
            }

            // If it already exists, just instantiate it
            try {
                Class<?> existing = Class.forName(targetName, false, cl);
                System.out.println("[MeowtilsInjectors][DEBUG] ModuleInjector: class already exists in CL, instantiating: " + existing);
                Object inst = tryInstantiate(existing);
                if (inst != null) return inst;
            } catch (ClassNotFoundException ignore) {
                System.out.println("[MeowtilsInjectors][DEBUG] ModuleInjector: class not present, will generate: " + targetName);
            }

            // Try to generate a subclass: public class <targetName> extends Module { public <init>() { super(displayName, key, Category.<categoryName>); } }
            byte[] bytes = generateSubclassBytes(targetName, displayName, categoryName, key, cl);
            if (bytes != null) {
                Class<?> defined = defineClass(cl, targetName, bytes);
                if (defined != null) {
                    System.out.println("[MeowtilsInjectors][DEBUG] ModuleInjector: defineClass success: " + defined + ", loader=" + defined.getClassLoader());
                    Object inst = tryInstantiate(defined);
                    if (inst != null) {
                        System.out.println("[MeowtilsInjectors][DEBUG] Generated runtime module class: " + targetName);
                        return inst;
                    }
                    System.out.println("[MeowtilsInjectors][DEBUG] ModuleInjector: instantiation failed after defineClass for " + targetName);
                }
                System.out.println("[MeowtilsInjectors][DEBUG] ModuleInjector: defineClass failed for " + targetName);
            }
            System.out.println("[MeowtilsInjectors][DEBUG] ModuleInjector: generateSubclassBytes returned null for " + targetName);
        } catch (Throwable t) {
            System.out.println("[MeowtilsInjectors][DEBUG] createOrLoadRuntimeModule failed: " + t);
            t.printStackTrace();
        }

        // Fallback: construct GUI and rename
        try {
            ClassLoader cl = ModuleInjector.class.getClassLoader();
            Class<?> guiCls = Class.forName("wtf.tatp.meowtils.modules.GUI", true, cl);
            Object placeholder = tryInstantiate(guiCls);
            if (placeholder != null) {
                setModuleName(placeholder, displayName);
                System.out.println("[MeowtilsInjectors][DEBUG] Falling back to GUI placeholder for module '" + displayName + "'");
                return placeholder;
            }
        } catch (Throwable t) {
            System.out.println("[MeowtilsInjectors][DEBUG] GUI placeholder fallback failed: " + t);
        }
        return null;
    }

    private static void tryWriteSourceTemplate(String sanitizedSimple, String displayName, String categoryName) {
        try {
            // Resolve category token for source (best-effort normalization)
            String catToken = categoryName == null ? "Utility" : categoryName.trim();
            if (catToken.isEmpty()) catToken = "Utility";
            // Config dir
            Class<?> loaderCls = Class.forName("net.minecraftforge.fml.common.Loader");
            Object loader = loaderCls.getMethod("instance").invoke(null);
            File cfgDir = (File) loaderCls.getMethod("getConfigDir").invoke(loader);
            File base = new File(cfgDir, "MeowtilsInjectors/modules-src");
            if (!base.exists()) base.mkdirs();
            File javaFile = new File(base, sanitizedSimple + ".java");
            if (javaFile.exists()) return; // don't overwrite user edits
            String src = generateJavaTemplateSource(sanitizedSimple, displayName, catToken);
            Files.write(javaFile.toPath(), src.getBytes(StandardCharsets.UTF_8), StandardOpenOption.CREATE);
            System.out.println("[MeowtilsInjectors][DEBUG] Wrote module source template: " + javaFile.getAbsolutePath());
        } catch (Throwable t) {
            System.out.println("[MeowtilsInjectors][DEBUG] Could not write source template: " + t);
        }
    }

    private static String buildCompilerClasspath(ClassLoader cl) {
        StringBuilder sb = new StringBuilder(System.getProperty("java.class.path", ""));
        try {
            // Try URLClassLoader chain
            ClassLoader cur = cl;
            while (cur != null) {
                if (cur instanceof java.net.URLClassLoader) {
                    for (URL u : ((URLClassLoader) cur).getURLs()) {
                        if (sb.length() > 0) sb.append(File.pathSeparator);
                        sb.append(new File(u.toURI()).getAbsolutePath());
                    }
                }
                cur = cur.getParent();
            }
        } catch (Throwable ignored) {}
        // Try LaunchClassLoader sources
        try {
            Class<?> lcl = Class.forName("net.minecraft.launchwrapper.LaunchClassLoader");
            if (lcl.isInstance(cl)) {
                try {
                    // Method getSources() -> List<URL>
                    Method m = lcl.getDeclaredMethod("getSources");
                    m.setAccessible(true);
                    java.util.List list = (java.util.List) m.invoke(cl);
                    if (list != null) {
                        for (Object o : list) {
                            if (o instanceof URL) {
                                URL u = (URL) o;
                                if (sb.length() > 0) sb.append(File.pathSeparator);
                                sb.append(new File(u.toURI()).getAbsolutePath());
                            }
                        }
                    }
                } catch (NoSuchMethodException nsme) {
                    // Fallback: field 'sources'
                    try {
                        java.lang.reflect.Field f = lcl.getDeclaredField("sources");
                        f.setAccessible(true);
                        Object v = f.get(cl);
                        if (v instanceof java.util.List) {
                            for (Object o : (java.util.List) v) {
                                if (o instanceof URL) {
                                    URL u = (URL) o;
                                    if (sb.length() > 0) sb.append(File.pathSeparator);
                                    sb.append(new File(u.toURI()).getAbsolutePath());
                                }
                            }
                        }
                    } catch (Throwable ignored) {}
                }
            }
        } catch (Throwable ignored) {}
        // Also include CodeSource locations for key anchor classes
        try {
            // Minecraft
            try {
                Class<?> mc = Class.forName("net.minecraft.client.Minecraft", false, cl);
                URL loc = mc.getProtectionDomain() != null && mc.getProtectionDomain().getCodeSource() != null ? mc.getProtectionDomain().getCodeSource().getLocation() : null;
                if (loc != null) {
                    if (sb.length() > 0) sb.append(File.pathSeparator);
                    sb.append(new File(loc.toURI()).getAbsolutePath());
                }
            } catch (Throwable ignored) {}
            // Forge Loader
            try {
                Class<?> forge = Class.forName("net.minecraftforge.fml.common.Loader", false, cl);
                URL loc = forge.getProtectionDomain() != null && forge.getProtectionDomain().getCodeSource() != null ? forge.getProtectionDomain().getCodeSource().getLocation() : null;
                if (loc != null) {
                    if (sb.length() > 0) sb.append(File.pathSeparator);
                    sb.append(new File(loc.toURI()).getAbsolutePath());
                }
            } catch (Throwable ignored) {}
            // Sponge Mixin (if present)
            try {
                Class<?> mixin = Class.forName("org.spongepowered.asm.mixin.Mixin", false, cl);
                URL loc = mixin.getProtectionDomain() != null && mixin.getProtectionDomain().getCodeSource() != null ? mixin.getProtectionDomain().getCodeSource().getLocation() : null;
                if (loc != null) {
                    if (sb.length() > 0) sb.append(File.pathSeparator);
                    sb.append(new File(loc.toURI()).getAbsolutePath());
                }
            } catch (Throwable ignored) {}
            // Our own mod jar/classes
            try {
                Class<?> self = ModuleInjector.class;
                URL loc = self.getProtectionDomain() != null && self.getProtectionDomain().getCodeSource() != null ? self.getProtectionDomain().getCodeSource().getLocation() : null;
                if (loc != null) {
                    if (sb.length() > 0) sb.append(File.pathSeparator);
                    sb.append(new File(loc.toURI()).getAbsolutePath());
                }
            } catch (Throwable ignored) {}
        } catch (Throwable ignored) {}
        return sb.toString();
    }

    // Computes SHA-1 of a byte array for debug logging
    private static String sha1(byte[] data) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-1");
            byte[] d = md.digest(data);
            StringBuilder sb = new StringBuilder(d.length * 2);
            for (byte b : d) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (Throwable t) {
            return "(sha1-failed)";
        }
    }

    private static Object tryCompileAndLoadFromSource(String targetName) {
        try {
            String simple = targetName.substring(targetName.lastIndexOf('.') + 1);
            Class<?> loaderCls = Class.forName("net.minecraftforge.fml.common.Loader");
            Object loader = loaderCls.getMethod("instance").invoke(null);
            File cfgDir = (File) loaderCls.getMethod("getConfigDir").invoke(loader);
            File srcDir = new File(cfgDir, "MeowtilsInjectors/modules-src");
            File outDir = new File(cfgDir, "MeowtilsInjectors/modules-classes");
            if (!outDir.exists()) outDir.mkdirs();
            File javaFile = new File(srcDir, simple + ".java");
            if (!javaFile.exists()) return null;

            // Debug: print source stats and hash
            try {
                byte[] srcBytes = Files.readAllBytes(javaFile.toPath());
                System.out.println("[MeowtilsInjectors][DEBUG] Source file: " + javaFile.getAbsolutePath() +
                        " | size=" + srcBytes.length + " | modified=" + new java.util.Date(javaFile.lastModified()) +
                        " | sha1=" + sha1(srcBytes));
            } catch (Throwable ignored) {}

            // Attempt to auto-inject or upgrade a latest-instance guard into setState to prevent stale instances from printing
            try {
                String srcTxt = new String(Files.readAllBytes(javaFile.toPath()), java.nio.charset.StandardCharsets.UTF_8);
                if (!srcTxt.contains("INJECTOR_LATEST_GUARD")) {
                    // naive insertion: after the first occurrence of "if (state) {" inside setState
                    int setStateIdx = srcTxt.indexOf("void setState(boolean state)");
                    int insertPos = -1;
                    if (setStateIdx >= 0) {
                        int ifIdx = srcTxt.indexOf("if (state)", setStateIdx);
                        if (ifIdx >= 0) {
                            int braceIdx = srcTxt.indexOf('{', ifIdx);
                            if (braceIdx >= 0) insertPos = braceIdx + 1;
                        }
                    }
                    if (insertPos >= 0) {
                        String guard = "\n        // INJECTOR_LATEST_GUARD: prevent stale instances from side-effects\n        try { Object latest = com.example.InjectionState.latestInjectedModuleInstances.get(com.example.InjectionState.normalizeName(modulename)); if (latest != null && latest != this) { return; } } catch (Throwable ignored) {}\n";
                        String newSrc = srcTxt.substring(0, insertPos) + guard + srcTxt.substring(insertPos);
                        Files.write(javaFile.toPath(), newSrc.getBytes(java.nio.charset.StandardCharsets.UTF_8));
                        System.out.println("[MeowtilsInjectors][DEBUG] Injected latest-instance guard into " + javaFile.getName());
                    }
                } else {
                    // Upgrade older guard form (without null-check) to include latest != null
                    String oldForm = "if (latest != this) { return; }";
                    if (srcTxt.contains(oldForm)) {
                        String upgraded = srcTxt.replace(oldForm, "if (latest != null && latest != this) { return; }");
                        if (!upgraded.equals(srcTxt)) {
                            Files.write(javaFile.toPath(), upgraded.getBytes(java.nio.charset.StandardCharsets.UTF_8));
                            System.out.println("[MeowtilsInjectors][DEBUG] Upgraded latest-instance guard in " + javaFile.getName());
                        }
                    }
                }
            } catch (Throwable t) {
                System.out.println("[MeowtilsInjectors][DEBUG] Could not inject latest-instance guard: " + t);
            }

            // Delete any previous compiled class to avoid stale loads
            Path classPathPre = new File(outDir, targetName.replace('.', '/') + ".class").toPath();
            try { Files.deleteIfExists(classPathPre); } catch (Throwable ignored) {}

            JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
            if (compiler == null) {
                System.out.println("[MeowtilsInjectors][DEBUG] No JavaCompiler available; skipping source compile for " + javaFile.getName());
                return null;
            }
            try (StandardJavaFileManager fm = compiler.getStandardFileManager(null, Locale.getDefault(), StandardCharsets.UTF_8)) {
                Iterable<?> units = fm.getJavaFileObjectsFromFiles(Arrays.asList(javaFile));
                // Build classpath from current classloader if possible (include LaunchClassLoader sources)
                String cp = buildCompilerClasspath(ModuleInjector.class.getClassLoader());
                // Diagnostics: dump CP
                try {
                    File dump = new File(cfgDir, "MeowtilsInjectors/last-compiler-classpath.txt");
                    dump.getParentFile().mkdirs();
                    String[] entries = cp.split(java.util.regex.Pattern.quote(File.pathSeparator));
                    StringBuilder dbg = new StringBuilder();
                    dbg.append("Java: ").append(System.getProperty("java.version")).append("\n");
                    dbg.append("OS: ").append(System.getProperty("os.name")).append(" ").append(System.getProperty("os.arch")).append("\n");
                    dbg.append("Entries (" + entries.length + "):\n");
                    for (String e : entries) dbg.append(" - ").append(e).append("\n");
                    java.nio.file.Files.write(dump.toPath(), dbg.toString().getBytes(StandardCharsets.UTF_8), java.nio.file.StandardOpenOption.CREATE, java.nio.file.StandardOpenOption.TRUNCATE_EXISTING);
                    System.out.println("[MeowtilsInjectors][DEBUG] Compiler CP entries=" + entries.length + "; wrote dump to " + dump.getAbsolutePath());
                } catch (Throwable ignored) {}
                // Also print anchor class locations
                try {
                    Class<?> mc = Class.forName("net.minecraft.client.Minecraft", false, ModuleInjector.class.getClassLoader());
                    System.out.println("[MeowtilsInjectors][DEBUG] Anchor Minecraft CodeSource=" + (mc.getProtectionDomain() != null && mc.getProtectionDomain().getCodeSource() != null ? mc.getProtectionDomain().getCodeSource().getLocation() : null));
                } catch (Throwable t) {
                    System.out.println("[MeowtilsInjectors][DEBUG] Anchor Minecraft not resolvable in CL: " + t);
                }
                try {
                    Class<?> forge = Class.forName("net.minecraftforge.fml.common.Loader", false, ModuleInjector.class.getClassLoader());
                    System.out.println("[MeowtilsInjectors][DEBUG] Anchor Forge Loader CodeSource=" + (forge.getProtectionDomain() != null && forge.getProtectionDomain().getCodeSource() != null ? forge.getProtectionDomain().getCodeSource().getLocation() : null));
                } catch (Throwable ignored) {}
                DiagnosticCollector<JavaFileObject> diags = new DiagnosticCollector<>();
                String[] opts = new String[] {"-cp", cp, "-d", outDir.getAbsolutePath(), "-source", "1.8", "-target", "1.8"};
                boolean ok = compiler.getTask(null, fm, diags, Arrays.asList(opts), null, (Iterable) units).call();
                int rc = ok ? 0 : 1;
                if (rc != 0) {
                    System.out.println("[MeowtilsInjectors][DEBUG] Compilation failed for " + javaFile.getAbsolutePath());
                    try {
                        for (Diagnostic<? extends JavaFileObject> d : diags.getDiagnostics()) {
                            System.out.println("[MeowtilsInjectors][JAVAC] " + d.getKind() + " @" + d.getLineNumber() + ": " + d.getMessage(null) + (d.getSource() != null ? (" in " + d.getSource().getName()) : ""));
                        }
                    } catch (Throwable ignored) {}
                    return null;
                }
            }

            // Try hot-reload using a child-first loader so we can reload same FQCN even if parent already has it
            try {
                ChildFirstLoader child = new ChildFirstLoader(ModuleInjector.class.getClassLoader(), outDir, targetName);
                Class<?> reloaded = child.loadClass(targetName, true);
                Object inst = tryInstantiate(reloaded);
                if (inst != null) {
                    // Debug: compiled class stats
                    try {
                        Path classPath = new File(outDir, targetName.replace('.', '/') + ".class").toPath();
                        byte[] clsBytes = Files.readAllBytes(classPath);
                        System.out.println("[MeowtilsInjectors][DEBUG] Loaded via ChildFirstLoader | class=" + reloaded +
                                " | loader=" + reloaded.getClassLoader() +
                                " | compiled.size=" + clsBytes.length +
                                " | compiled.modified=" + new java.util.Date(Files.getLastModifiedTime(classPath).toMillis()) +
                                " | compiled.sha1=" + sha1(clsBytes));
                    } catch (Throwable ignored) {}
                    return inst;
                }
            } catch (Throwable t) {
                System.out.println("[MeowtilsInjectors][DEBUG] ChildFirstLoader hot-reload path failed: " + t);
            }

            // Fallback: load compiled bytes into the existing ClassLoader (may fail if already defined)
            Path classPath = new File(outDir, targetName.replace('.', '/') + ".class").toPath();
            if (!Files.exists(classPath)) {
                System.out.println("[MeowtilsInjectors][DEBUG] Compiled class not found at " + classPath);
                return null;
            }
            byte[] bytes = Files.readAllBytes(classPath);
            System.out.println("[MeowtilsInjectors][DEBUG] Fallback defineClass path | compiled.size=" + bytes.length +
                    " | compiled.modified=" + new java.util.Date(Files.getLastModifiedTime(classPath).toMillis()) +
                    " | compiled.sha1=" + sha1(bytes));
            Class<?> defined = defineClass(ModuleInjector.class.getClassLoader(), targetName, bytes);
            if (defined == null) return null;
            return tryInstantiate(defined);
        } catch (Throwable t) {
            System.out.println("[MeowtilsInjectors][DEBUG] tryCompileAndLoadFromSource failed: " + t);
            return null;
        }
    }

    /**
     * Minimal child-first loader that loads exactly one target class name from a directory of compiled classes,
     * delegating everything else to the parent. This allows reloading the same FQCN without restart.
     */
    private static final class ChildFirstLoader extends ClassLoader {
        private final File outDir;
        private final String targetName;

        ChildFirstLoader(ClassLoader parent, File outDir, String targetName) {
            super(parent);
            this.outDir = outDir;
            this.targetName = targetName;
        }

        @Override
        protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
            // For the target class, try loading our compiled bytes first
            if (name.equals(this.targetName)) {
                try {
                    byte[] bytes = readBytesFor(name);
                    if (bytes != null) {
                        Class<?> c = defineClass(name, bytes, 0, bytes.length);
                        if (resolve) resolveClass(c);
                        return c;
                    }
                } catch (Throwable ignored) {}
            }
            // Fallback to parent-first for everything else
            return super.loadClass(name, resolve);
        }

        private byte[] readBytesFor(String name) {
            try {
                File f = new File(outDir, name.replace('.', File.separatorChar) + ".class");
                if (!f.exists()) return null;
                return Files.readAllBytes(f.toPath());
            } catch (Throwable ignored) {
                return null;
            }
        }
    }

    private static String generateJavaTemplateSource(String simple, String displayName, String category) {
        StringBuilder sb = new StringBuilder();
        sb.append("package wtf.tatp.meowtils.modules;\n\n");
        sb.append("import net.minecraft.client.Minecraft;\n");
        sb.append("import net.minecraftforge.common.MinecraftForge;\n");
        sb.append("import net.minecraftforge.fml.common.FMLCommonHandler;\n");
        sb.append("import wtf.tatp.meowtils.gui.Module;\n");
        sb.append("// Optional runtime-only references (accessed via reflection in mixins)\n");
        sb.append("// import wtf.tatp.meowtils.Meowtils;\n");
        sb.append("// import wtf.tatp.meowtils.config.ConfigHandler;\n\n");
        sb.append("public class ").append(simple).append(" extends Module {\n\n");
        sb.append("    public static final String INJECTOR_TAG = \"MeowtilsInjectors\";\n");
        sb.append("    public static final String modulename = \"").append(displayName.replace("\\", "\\\\").replace("\"", "\\\"")).append("\";\n\n");
        sb.append("    public ").append(simple).append("() {\n");
        sb.append("        super(modulename, 0, Category.").append(category).append(");\n");
        sb.append("    }\n\n");
        sb.append("    @Override\n");
        sb.append("    public void setState(boolean state) {\n");
        sb.append("        super.setState(state);\n");
        sb.append("        if (state) {\n");
        sb.append("            try { MinecraftForge.EVENT_BUS.register(this); } catch (Throwable ignored) {}\n");
        sb.append("            try { FMLCommonHandler.instance().bus().register(this); } catch (Throwable ignored) {}\n");
        sb.append("        } else {\n");
        sb.append("            try { MinecraftForge.EVENT_BUS.unregister(this); } catch (Throwable ignored) {}\n");
        sb.append("            try { FMLCommonHandler.instance().bus().unregister(this); } catch (Throwable ignored) {}\n");
        sb.append("        }\n");
        sb.append("        // Toggle messages are handled by a mixin to avoid compile-time deps on Meowtils classes.\n");
        sb.append("    }\n\n");
        sb.append("}\n");
        return sb.toString();
    }

    private static String sanitizeSimpleName(String in) {
        if (in == null || in.isEmpty()) return "Injected";
        // Strip color codes (§x) and &x
        String s = in.replaceAll("\u00A7.", "").replaceAll("&.", "");
        // Remove non-java identifier chars and ensure it starts with letter/_
        s = s.replaceAll("[^A-Za-z0-9_]", "");
        if (s.isEmpty() || !Character.isJavaIdentifierStart(s.charAt(0))) s = "Injected" + s;
        return s;
    }

    private static Object tryInstantiate(Class<?> cls) {
        try {
            Constructor<?> c = cls.getDeclaredConstructor();
            c.setAccessible(true);
            return c.newInstance();
        } catch (Throwable ignored) {
        }
        // Try any no-arg constructor
        try {
            for (Constructor<?> c : cls.getDeclaredConstructors()) {
                if (c.getParameterCount() == 0) {
                    c.setAccessible(true);
                    return c.newInstance();
                }
            }
        } catch (Throwable ignored) {
        }
        return null;
    }

    private static void setModuleName(Object module, String name) {
        try {
            Method m = module.getClass().getMethod("setName", String.class);
            m.invoke(module, name);
        } catch (Throwable t) {
            try {
                Method m = module.getClass().getSuperclass().getDeclaredMethod("setName", String.class);
                m.setAccessible(true);
                m.invoke(module, name);
            } catch (Throwable ignored) {
            }
        }
    }

    private static Class<?> defineClass(ClassLoader cl, String name, byte[] bytes) {
        try {
            // Try LaunchClassLoader specific defineClass(String, byte[])
            try {
                Class<?> lcl = Class.forName("net.minecraft.launchwrapper.LaunchClassLoader");
                if (lcl.isInstance(cl)) {
                    try {
                        Method m = lcl.getDeclaredMethod("defineClass", String.class, byte[].class);
                        m.setAccessible(true);
                        Class<?> c = (Class<?>) m.invoke(cl, name, bytes);
                        System.out.println("[MeowtilsInjectors][DEBUG] ModuleInjector: LaunchClassLoader#defineClass ok for '" + name + "'");
                        return c;
                    } catch (NoSuchMethodException ignored) {
                        // Fallback to ClassLoader#defineClass
                        System.out.println("[MeowtilsInjectors][DEBUG] ModuleInjector: LaunchClassLoader#defineClass not found, trying Unsafe#defineClass for '" + name + "'");
                    }
                }
            } catch (ClassNotFoundException ignored) {}

            // Fallback: Unsafe#defineClass
            try {
                Class<?> unsafeCls = Class.forName("sun.misc.Unsafe");
                Field f = unsafeCls.getDeclaredField("theUnsafe");
                f.setAccessible(true);
                Object unsafe = f.get(null);
                Method m = unsafeCls.getMethod("defineClass", String.class, byte[].class, int.class, int.class, ClassLoader.class, ProtectionDomain.class);
                ProtectionDomain pd = ModuleInjector.class.getProtectionDomain();
                Class<?> c = (Class<?>) m.invoke(unsafe, name, bytes, 0, bytes.length, cl, pd);
                System.out.println("[MeowtilsInjectors][DEBUG] ModuleInjector: Unsafe#defineClass ok for '" + name + "'");
                return c;
            } catch (Throwable t) {
                System.out.println("[MeowtilsInjectors][DEBUG] Unsafe#defineClass failed: " + t);
            }
        } catch (Throwable t) {
            System.out.println("[MeowtilsInjectors][DEBUG] defineClass failed: " + t);
        }
        return null;
    }

    private static byte[] generateSubclassBytes(String targetName, String displayName, String categoryName, int key, ClassLoader cl) {
        try {
            // Resolve super and category types for descriptors
            Class<?> superModule = Class.forName("wtf.tatp.meowtils.gui.Module", false, cl);
            Class<?> categoryEnum = Class.forName("wtf.tatp.meowtils.gui.Module$Category", false, cl);

            // Load ASM reflectively
            Class<?> cwCls = Class.forName("org.objectweb.asm.ClassWriter", false, cl);
            Class<?> fvCls = Class.forName("org.objectweb.asm.FieldVisitor", false, cl);
            Class<?> mvCls = Class.forName("org.objectweb.asm.MethodVisitor", false, cl);
            Class<?> tpCls = Class.forName("org.objectweb.asm.Type", false, cl);
            Class<?> opcCls = Class.forName("org.objectweb.asm.Opcodes", false, cl);

            int ACC_PUBLIC = (int) opcCls.getField("ACC_PUBLIC").get(null);
            int ACC_SUPER = (int) opcCls.getField("ACC_SUPER").get(null);

            // new ClassWriter(ClassWriter.COMPUTE_MAXS | ClassWriter.COMPUTE_FRAMES)
            int COMPUTE_MAXS = (int) cwCls.getField("COMPUTE_MAXS").get(null);
            int COMPUTE_FRAMES = (int) cwCls.getField("COMPUTE_FRAMES").get(null);
            Object cw = cwCls.getConstructor(int.class).newInstance(COMPUTE_MAXS | COMPUTE_FRAMES);

            // visit(V1_8, ACC_PUBLIC|ACC_SUPER, internalName, null, superInternal, null)
            int V1_8 = (int) opcCls.getField("V1_8").get(null);
            Method visit = cwCls.getMethod("visit", int.class, int.class, String.class, String.class, String.class, String[].class);
            String internalName = targetName.replace('.', '/');
            String superInternal = superModule.getName().replace('.', '/');
            visit.invoke(cw, V1_8, ACC_PUBLIC | ACC_SUPER, internalName, null, superInternal, null);

            // Add: public static final String INJECTOR_TAG = "MeowtilsInjectors";
            Method visitField = cwCls.getMethod("visitField", int.class, String.class, String.class, String.class, Object.class);
            int ACC_STATIC = (int) opcCls.getField("ACC_STATIC").get(null);
            int ACC_FINAL = (int) opcCls.getField("ACC_FINAL").get(null);
            String strDesc = (String) tpCls.getMethod("getDescriptor", Class.class).invoke(null, String.class);
            Object fv = visitField.invoke(cw, ACC_PUBLIC | ACC_STATIC | ACC_FINAL, "INJECTOR_TAG", strDesc, null, "MeowtilsInjectors");
            Method fvVisitEnd = fvCls.getMethod("visitEnd");
            fvVisitEnd.invoke(fv);

            // Add: public static final String modulename = <displayName>;
            fv = visitField.invoke(cw, ACC_PUBLIC | ACC_STATIC | ACC_FINAL, "modulename", strDesc, null, displayName);
            fvVisitEnd.invoke(fv);

            // Generate default constructor
            Method visitMethod = cwCls.getMethod("visitMethod", int.class, String.class, String.class, String.class, String[].class);
            Object mv = visitMethod.invoke(cw, ACC_PUBLIC, "<init>", "()V", null, null);

            // Helper to emit bytecode via MethodVisitor
            // mv.visitVarInsn(ALOAD, 0)
            Method visitVarInsn = mvCls.getMethod("visitVarInsn", int.class, int.class);
            int ALOAD = (int) opcCls.getField("ALOAD").get(null);
            int INVOKESPECIAL = (int) opcCls.getField("INVOKESPECIAL").get(null);
            int RETURN = (int) opcCls.getField("RETURN").get(null);
            int LDC = (int) opcCls.getField("LDC").get(null);
            int GETSTATIC = (int) opcCls.getField("GETSTATIC").get(null);

            // mv.visitVarInsn(ALOAD, 0)
            visitVarInsn.invoke(mv, ALOAD, 0);

            // Push displayName
            Method visitLdcInsn = mvCls.getMethod("visitLdcInsn", Object.class);
            visitLdcInsn.invoke(mv, displayName);

            // Push key (int)
            Method visitIntInsn = mvCls.getMethod("visitIntInsn", int.class, int.class);
            int BIPUSH = (int) opcCls.getField("BIPUSH").get(null);
            int SIPUSH = (int) opcCls.getField("SIPUSH").get(null);
            if (key >= -128 && key <= 127) {
                visitIntInsn.invoke(mv, BIPUSH, key);
            } else if (key >= -32768 && key <= 32767) {
                visitIntInsn.invoke(mv, SIPUSH, key);
            } else {
                // fallback: load 0
                visitIntInsn.invoke(mv, BIPUSH, 0);
            }

            // Resolve Category enum constant (case-insensitive, default to Utility)
            String resolvedCategoryName = null;
            try {
                Object[] consts = categoryEnum.getEnumConstants();
                if (consts != null) {
                    for (Object c : consts) {
                        String n = ((Enum<?>) c).name();
                        if (n.equalsIgnoreCase(categoryName)) { resolvedCategoryName = n; break; }
                    }
                }
            } catch (Throwable ignored) {}
            if (resolvedCategoryName == null) {
                resolvedCategoryName = "Utility";
                System.out.println("[MeowtilsInjectors][DEBUG] Unknown category '" + categoryName + "', defaulting to 'Utility'.");
            }
            Object catConst = Enum.valueOf((Class<Enum>) categoryEnum, resolvedCategoryName);
            // mv.getstatic Module$Category.<category>
            Method visitFieldInsn = mvCls.getMethod("visitFieldInsn", int.class, String.class, String.class, String.class);
            visitFieldInsn.invoke(mv, GETSTATIC,
                    categoryEnum.getName().replace('.', '/'),
                    ((Enum<?>) catConst).name(),
                    (String) tpCls.getMethod("getDescriptor", Class.class).invoke(null, categoryEnum));

            // Call super constructor Module(String,int,Category)
            Method visitMethodInsn = mvCls.getMethod("visitMethodInsn", int.class, String.class, String.class, String.class, boolean.class);
            String superCtorDesc = "(Ljava/lang/String;IL" + categoryEnum.getName().replace('.', '/') + ";)V";
            visitMethodInsn.invoke(mv, INVOKESPECIAL, superInternal, "<init>", superCtorDesc, false);

            // return
            Method visitInsn = mvCls.getMethod("visitInsn", int.class);
            visitInsn.invoke(mv, RETURN);

            // mv.visitMaxs(0,0) and mv.visitEnd()
            Method visitMaxs = mvCls.getMethod("visitMaxs", int.class, int.class);
            Method visitEnd = mvCls.getMethod("visitEnd");
            visitMaxs.invoke(mv, 0, 0);
            visitEnd.invoke(mv);

            // cw.visitEnd()
            visitEnd = cwCls.getMethod("visitEnd");
            visitEnd.invoke(cw);

            // toByteArray
            Method toByteArray = cwCls.getMethod("toByteArray");
            return (byte[]) toByteArray.invoke(cw);
        } catch (Throwable t) {
            System.out.println("[MeowtilsInjectors][DEBUG] generateSubclassBytes failed: " + t);
            t.printStackTrace();
            return null;
        }
    }
}
