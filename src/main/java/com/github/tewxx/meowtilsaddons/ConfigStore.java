package com.github.tewxx.meowtilsaddons;

import net.minecraftforge.fml.common.Loader;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.util.Properties;

public final class ConfigStore {
    private ConfigStore() {}

    public static class RejectsFrameCfg {
        public int x = 5;
        public int y = 5;
        public boolean open = false;
    }

    private static File getStoreFile() {
        File cfgDir = Loader.instance().getConfigDir();
        File myDir = new File(cfgDir, "MeowtilsInjectors");
        if (!myDir.exists()) myDir.mkdirs();
        return new File(myDir, "rejects-frame.properties");
    }

    public static RejectsFrameCfg readRejectsFrame() {
        RejectsFrameCfg out = new RejectsFrameCfg();
        try {
            File f = getStoreFile();
            if (!f.exists()) return out;
            Properties p = new Properties();
            try (FileInputStream fis = new FileInputStream(f)) { p.load(fis); }
            out.x = parseInt(p.getProperty("x"), out.x);
            out.y = parseInt(p.getProperty("y"), out.y);
            out.open = Boolean.parseBoolean(p.getProperty("open", String.valueOf(out.open)));
        } catch (Throwable ignored) {}
        return out;
    }

    public static void writeRejectsFrame(int x, int y, boolean open) {
        try {
            Properties p = new Properties();
            p.setProperty("x", String.valueOf(x));
            p.setProperty("y", String.valueOf(y));
            p.setProperty("open", String.valueOf(open));
            File f = getStoreFile();
            try (FileOutputStream fos = new FileOutputStream(f)) { p.store(fos, "MeowtilsAddons Rejects frame"); }
        } catch (Throwable ignored) {}
    }

    private static int parseInt(String s, int def) {
        try { return Integer.parseInt(s); } catch (Throwable t) { return def; }
    }
}
