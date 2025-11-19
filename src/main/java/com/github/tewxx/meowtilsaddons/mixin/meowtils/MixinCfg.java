package com.github.tewxx.meowtilsaddons.mixin.meowtils;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;

@Pseudo
@Mixin(targets = "wtf.tatp.meowtils.config.cfg", remap = false)
public class MixinCfg {

    public boolean autoFish = false;
    public int autoFishKey = 0;

    public boolean speedMine = false;
    public int speedMineKey = 0;
    public int speedMineDelay = 0;
    public int speedMineSpeed = 60;

    public boolean levelFaker = false;
    public int levelFakerKey = 0;

    public boolean networkLevelEnabledBool = true;
    public int networkLevel = 1;
    public int networkLevelEnabled = 1;
    public int bedwarsLevel = 1;

    public boolean bedwarsScoreboardEnabledBool = true;
    public boolean bedwarsChatEnabledBool = true;
    public boolean bedwarsXpEnabledBool = true;

    public boolean freecam = false;
    public int freecamKey = 0;

    public boolean killInsults = false;
    public int killInsultsKey = 0;
    public String killInsultsMode = "Message";

    public int rejectsCategoryX = 5;
    public int rejectsCategoryY = 125;
    public boolean rejectsCategoryExpanded = true;
}
