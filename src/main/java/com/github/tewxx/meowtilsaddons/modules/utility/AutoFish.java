package com.github.tewxx.meowtilsaddons.modules.utility;

import wtf.tatp.meowtils.gui.Module;

/**
 * Minimal addon module to prove injection. Empty behavior; just shows in GUI.
 */
public class AutoFish extends Module {
    public AutoFish() {
        // Match Meowtils constructor pattern: (name, keybindId, commandId, category)
        super("AutoFish", "autoFishKey", "autofish", Module.Category.Utility);
        try { this.tooltip("Injected test module (no behavior)"); } catch (Throwable ignored) {}
    }
}
