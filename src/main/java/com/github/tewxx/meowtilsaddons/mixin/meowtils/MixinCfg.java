package com.github.tewxx.meowtilsaddons.mixin.meowtils;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;

/**
 * Adds missing config fields for our injected module so Meowtils' cfg JSON
 * persists and loads them like native modules.
 */
@Pseudo
@Mixin(targets = "wtf.tatp.meowtils.config.cfg", remap = false)
public class MixinCfg {
    // Mirror Meowtils naming convention: <feature> and <feature>Key
    public boolean autoFish = false;
    public int autoFishKey = 0;

    public boolean speedMine = false;
    public int speedMineKey = 0;
    public int speedMineDelay = 0;       // 0..4
    public int speedMineSpeed = 60;      // percent 1..100

    // Added frame persistence for dynamically injected Category.Rejects
    public int rejectsCategoryX = 5;
    public int rejectsCategoryY = 125;
    public boolean rejectsCategoryExpanded = false;
}
