package wtf.tatp.meowtils.modules.advanced;

import wtf.tatp.meowtils.gui.Module;

public class LevelFaker extends Module {
    public LevelFaker() {
        super("LevelFaker", "levelFakerKey", "levelFaker", Module.Category.Advanced);
        try { this.tooltip("Fake Hypixel level display (stub)"); } catch (Throwable ignored) {}
    }
}

