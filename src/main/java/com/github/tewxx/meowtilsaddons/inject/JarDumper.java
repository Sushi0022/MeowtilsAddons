package com.github.tewxx.meowtilsaddons.inject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.JarURLConnection;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.HashMap;
import java.util.List;
import java.util.Collection;
import java.util.Locale;
import java.util.Map;
import java.nio.charset.StandardCharsets;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;
import net.minecraftforge.fml.common.Loader;
import net.minecraftforge.fml.common.ModContainer;
import net.minecraft.launchwrapper.Launch;
import net.minecraft.launchwrapper.LaunchClassLoader;

public final class JarDumper {
    private JarDumper() {}

    public static String dumpMeowtilsWithTransformed(String tag) {
        return dumpMeowtilsWithTransformed(tag, null);
    }

    private static void writeDebugArtifacts(ZipOutputStream zout) {
        try {
            // 1) Embed our addon AutoFish.class so you can inspect it alongside Meowtils
            String addonClsRes = "com/github/tewxx/meowtilsaddons/modules/utility/AutoFish.class";
            InputStream in = JarDumper.class.getClassLoader().getResourceAsStream(addonClsRes);
            if (in != null) {
                ByteArrayOutputStream bos = new ByteArrayOutputStream();
                byte[] buf = new byte[8192];
                int r; while ((r = in.read(buf)) > 0) bos.write(buf, 0, r);
                in.close();
                byte[] cls = bos.toByteArray();

                // Copy under META-INF/mta-debug
                ZipEntry e1 = new ZipEntry("META-INF/mta-debug/" + addonClsRes.substring(addonClsRes.lastIndexOf('/') + 1));
                zout.putNextEntry(e1);
                zout.write(cls);
                zout.closeEntry();

                // Also place a debug copy where you expect to find it when browsing the jar
                // (does not affect runtime classloading; purely for inspection)
                ZipEntry e2 = new ZipEntry("wtf/tatp/meowtils/modules/utility/AutoFish.class");
                zout.putNextEntry(e2);
                zout.write(cls);
                zout.closeEntry();
            }

            // 2) Embed the most recent module dump text if present (from Downloads/MeowtilsAddons-debug)
            File debugDir = new File(new File(System.getProperty("user.home"), "Downloads"), "MeowtilsAddons-debug");
            if (debugDir.isDirectory()) {
                File[] dumps = debugDir.listFiles((d, n) -> n.startsWith("modules-") && n.endsWith(".txt"));
                if (dumps != null && dumps.length > 0) {
                    // Pick the newest by lastModified
                    File newest = dumps[0];
                    for (File f : dumps) if (f.lastModified() > newest.lastModified()) newest = f;
                    try (FileInputStream fin = new FileInputStream(newest)) {
                        ZipEntry e = new ZipEntry("META-INF/mta-debug/" + newest.getName());
                        zout.putNextEntry(e);
                        byte[] buf = new byte[8192];
                        int r; while ((r = fin.read(buf)) > 0) zout.write(buf, 0, r);
                        zout.closeEntry();
                    } catch (Throwable ignored) { }
                }
            }

            // 3) Embed our modules list resource (so you can confirm what injector read)
            try (InputStream rin = JarDumper.class.getClassLoader().getResourceAsStream("meowtilsaddons_modules.txt")) {
                if (rin != null) {
                    ZipEntry re = new ZipEntry("META-INF/mta-debug/meowtilsaddons_modules.txt");
                    zout.putNextEntry(re);
                    byte[] buf = new byte[8192];
                    int r; while ((r = rin.read(buf)) > 0) zout.write(buf, 0, r);
                    zout.closeEntry();
                }
            } catch (Throwable ignored) { }

            // 4) Add a small README describing what to look for
            try {
                String readme = "MeowtilsAddons debug artifacts\n" +
                        "- ModuleManager.class should reference com/github/tewxx/meowtilsaddons/inject/ModuleInjector\n" +
                        "- AutoFish.class is included under META-INF/mta-debug and as wtf/.../AutoFish.class (debug copy)\n" +
                        "- modules-*.txt is the latest runtime dump showing actual module list\n";
                ZipEntry rd = new ZipEntry("META-INF/mta-debug/README.txt");
                zout.putNextEntry(rd);
                zout.write(readme.getBytes(StandardCharsets.UTF_8));
                zout.closeEntry();
            } catch (Throwable ignored) { }

            // No helper source files are embedded to avoid confusion.
        } catch (Throwable ignored) { }
    }

    public static String dumpMeowtilsWithTransformed(String tag, String exportDirOverride) {
        try {
            // Prefer resolving via Forge Loader (robust for dev and prod)
            File srcJar = resolveMeowtilsJarFromLoader();
            if (srcJar == null) {
                // Fallback: Locate Meowtils jar from a well-known class/code source
                URL url = Class.forName("wtf.tatp.meowtils.Meowtils").getProtectionDomain().getCodeSource().getLocation();
                srcJar = resolveFileFromURL(url);
            }
            if (!srcJar.exists()) {
                return fail("Source Meowtils location not found");
            }

            // Build map of transformed classes (path in jar -> bytes)
            Map<String, byte[]> transformed = new HashMap<>();

            // Prefer reading from Mixin export dir if available
            File mixinOut = exportDirOverride != null ? new File(exportDirOverride) : resolveMixinOutDir();
            if (mixinOut != null && mixinOut.isDirectory()) {
                Map<String, byte[]> m = loadTransformedClasses(mixinOut);
                transformed.putAll(m);
                if (m.isEmpty()) {
                    log("No transformed classes found under %s", mixinOut.getAbsolutePath());
                }
            } else {
                log("Mixin export dir not found (.mixin.out); falling back to LaunchClassLoader bytes");
            }

            // Always attempt LaunchClassLoader fallback for the primary target class
            byte[] mmBytes = getTransformedBytesViaLaunch("wtf.tatp.meowtils.gui.ModuleManager");
            if (mmBytes != null) {
                transformed.put("wtf/tatp/meowtils/gui/ModuleManager.class", mmBytes);
            }
            // Also attempt to overlay cfg.class if transformed
            byte[] cfgBytes = getTransformedBytesViaLaunch("wtf.tatp.meowtils.config.cfg");
            if (cfgBytes != null) {
                transformed.put("wtf/tatp/meowtils/config/cfg.class", cfgBytes);
            }

            // Prepare destination jar path under Downloads
            File baseDir = new File(System.getProperty("user.home"), "Downloads");
            File outDir = new File(baseDir, "MeowtilsAddons-debug");
            if (!outDir.exists() && !outDir.mkdirs()) {
                return fail("Failed to create directory %s", outDir.getAbsolutePath());
            }
            String name = String.format(Locale.ROOT, "meowtils-patched-%s-%d.jar", (tag == null || tag.isEmpty()) ? "dump" : tag, System.currentTimeMillis());
            File dstJar = new File(outDir, name);

            if (srcJar.isFile()) {
                // Copy original jar into output while overlaying transformed classes
                try (ZipInputStream zin = new ZipInputStream(new FileInputStream(srcJar));
                     ZipOutputStream zout = new ZipOutputStream(new FileOutputStream(dstJar))) {
                    ZipEntry e;
                    byte[] buf = new byte[8192];
                    while ((e = zin.getNextEntry()) != null) {
                        String entryName = e.getName();
                        byte[] override = transformed.remove(entryName); // overlay if transformed version exists
                        ZipEntry outEntry = new ZipEntry(entryName);
                        outEntry.setTime(e.getTime());
                        zout.putNextEntry(outEntry);
                        if (override != null) {
                            zout.write(override);
                        } else {
                            int len;
                            while ((len = zin.read(buf)) > 0) {
                                zout.write(buf, 0, len);
                            }
                        }
                        zout.closeEntry();
                        zin.closeEntry();
                    }
                    // Add any remaining transformed classes which didn't exist originally
                    for (Map.Entry<String, byte[]> rem : transformed.entrySet()) {
                        ZipEntry add = new ZipEntry(rem.getKey());
                        zout.putNextEntry(add);
                        zout.write(rem.getValue());
                        zout.closeEntry();
                    }
                    // Add debug artifacts to help inspection
                    writeDebugArtifacts(zout);
                }
            } else if (srcJar.isDirectory()) {
                // Create a new jar from directory contents, overlaying transformed classes
                try (ZipOutputStream zout = new ZipOutputStream(new FileOutputStream(dstJar))) {
                    Path base = srcJar.toPath();
                    Files.walk(base)
                        .filter(p -> Files.isRegularFile(p))
                        .forEach(p -> {
                            try {
                                String rel = base.relativize(p).toString().replace(File.separatorChar, '/');
                                // Normalize only classes and resources; include everything
                                byte[] override = transformed.remove(rel);
                                ZipEntry outEntry = new ZipEntry(rel);
                                zout.putNextEntry(outEntry);
                                if (override != null) {
                                    zout.write(override);
                                } else {
                                    Files.copy(p, zout);
                                }
                                zout.closeEntry();
                            } catch (IOException ignored) {}
                        });
                    // Add any remaining transformed classes
                    for (Map.Entry<String, byte[]> rem : transformed.entrySet()) {
                        ZipEntry add = new ZipEntry(rem.getKey());
                        zout.putNextEntry(add);
                        zout.write(rem.getValue());
                        zout.closeEntry();
                    }
                    // Add debug artifacts to help inspection
                    writeDebugArtifacts(zout);
                }
            }

            log("Patched jar written: %s", dstJar.getAbsolutePath());
            return dstJar.getAbsolutePath();
        } catch (ClassNotFoundException | URISyntaxException | IOException ex) {
            return fail("Dump failed: %s", ex.getMessage());
        }
    }

    private static File resolveMeowtilsJarFromLoader() {
        try {
            ModContainer mc = Loader.instance().getIndexedModList().get("meowtils");
            if (mc != null) {
                File src = mc.getSource();
                if (src != null && src.exists()) return src;
            }
        } catch (Throwable ignored) {}
        return null;
    }

    private static File resolveFileFromURL(URL url) throws URISyntaxException, IOException {
        if (url == null) return null;
        String proto = url.getProtocol();
        if ("file".equalsIgnoreCase(proto)) {
            return new File(url.toURI());
        }
        if ("jar".equalsIgnoreCase(proto)) {
            JarURLConnection conn = (JarURLConnection) url.openConnection();
            URL jarUrl = conn.getJarFileURL();
            return new File(jarUrl.toURI());
        }
        // Last resort: try plain path
        return new File(url.getPath());
    }

    private static File resolveMixinOutDir() {
        // Try common locations relative to working dir
        File wd = new File(System.getProperty("user.dir", "."));
        File[] candidates = new File[] {
            new File(wd, ".mixin.out"),
            new File(wd, "mixin.out"),
            new File(new File(wd, "run"), ".mixin.out"),
            new File(new File(wd, "run"), "mixin.out")
        };
        for (File f : candidates) {
            if (f.isDirectory()) return f;
        }
        // Search shallowly for any directory named ".mixin.out" or "mixin.out"
        File found = findDirByName(wd, ".mixin.out", 2);
        if (found != null) return found;
        found = findDirByName(wd, "mixin.out", 2);
        return found;
    }

    private static File findDirByName(File root, String name, int maxDepth) {
        if (maxDepth < 0 || root == null || !root.isDirectory()) return null;
        File[] list = root.listFiles();
        if (list == null) return null;
        for (File f : list) {
            if (f.getName().equals(name) && f.isDirectory()) return f;
        }
        for (File f : list) {
            if (f.isDirectory()) {
                File r = findDirByName(f, name, maxDepth - 1);
                if (r != null) return r;
            }
        }
        return null;
    }

    private static Map<String, byte[]> loadTransformedClasses(File mixinOut) throws IOException {
        Map<String, byte[]> map = new HashMap<>();
        Files.walk(mixinOut.toPath())
            .filter(p -> Files.isRegularFile(p) && p.toString().endsWith(".class"))
            .forEach(p -> {
                // Make entry name relative within export dir
                String rel = mixinOut.toPath().relativize(p).toString().replace(File.separatorChar, '/');
                // Normalize to jar entry name by stripping any prefix before the real package path
                int idx = rel.indexOf("wtf/tatp/meowtils/");
                if (idx > 0) rel = rel.substring(idx);
                // Only overlay meowtils package classes
                if (!rel.startsWith("wtf/tatp/meowtils/")) return;
                try {
                    map.put(rel, Files.readAllBytes(p));
                } catch (IOException ignored) {}
            });
        return map;
    }

    private static void ensureParentDirsInZip(ZipOutputStream zout, String entryName) throws IOException {
        int idx = entryName.lastIndexOf('/');
        if (idx <= 0) return;
        String path = entryName.substring(0, idx + 1);
        // No need to explicitly add dirs for most zips, but some tools prefer it.
        // We'll add a directory entry if not the root and not already added.
        // We can't easily track existing entries here; safe to skip.
    }

    private static byte[] getTransformedBytesViaLaunch(String classNameDot) {
        try {
            Object o = Launch.classLoader;
            if (o instanceof LaunchClassLoader) {
                LaunchClassLoader lcl = (LaunchClassLoader) o;
                String res = classNameDot.replace('.', '/') + ".class";
                InputStream in = lcl.getResourceAsStream(res);
                if (in != null) {
                    ByteArrayOutputStream bos = new ByteArrayOutputStream();
                    byte[] buf = new byte[8192];
                    int r;
                    while ((r = in.read(buf)) > 0) bos.write(buf, 0, r);
                    in.close();
                    byte[] arr = bos.toByteArray();
                    if (arr != null && arr.length > 0) {
                        log("Fetched transformed bytes for %s via LaunchClassLoader (%d bytes)", classNameDot, arr.length);
                        return arr;
                    }
                }
            }
        } catch (Throwable ignored) {}
        return null;
    }

    private static String fail(String fmt, Object... args) {
        String msg = String.format(Locale.ROOT, fmt, args);
        log(msg);
        return null;
    }

    private static void log(String fmt, Object... args) {
        try {
            System.out.println("[MeowtilsAddons] " + String.format(Locale.ROOT, fmt, args));
        } catch (Throwable ignored) {}
    }
}
