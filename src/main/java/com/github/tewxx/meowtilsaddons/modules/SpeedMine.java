package com.github.tewxx.meowtilsaddons.modules;

import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import wtf.tatp.meowtils.gui.Module;
import wtf.tatp.meowtils.gui.values.NumberValue;
import net.minecraft.util.MovingObjectPosition;
import com.github.tewxx.meowtilsaddons.mixin.minecraft.AccessorPlayerControllerMP;
import wtf.tatp.meowtils.config.cfg;

/**
 * SpeedMine with configurable speed percent and delay.
 */
public class SpeedMine extends Module {
    // Settings
    private final NumberValue speedPercent;
    private final NumberValue delayTicks;

    public SpeedMine() {
        super("SpeedMine", "speedMineKey", "speedMine", Module.Category.Advanced);
        try { this.tooltip("Allows you to mine blocks faster"); } catch (Throwable ignored) {}

        this.speedPercent = new NumberValue("Speed (%)", 5.0, 100.0, 5.0, "%", "speedMineSpeed", Integer.TYPE);
        this.delayTicks = new NumberValue("Delay", 0.0, 4.0, 1.0, null, "speedMineDelay", Integer.TYPE);

        this.addValue(speedPercent);
        this.addValue(delayTicks);
    }

    @Override
    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (this.mc == null || this.mc.thePlayer == null || this.mc.theWorld == null) return;
        if (event.phase != TickEvent.Phase.START) return;
        if (!this.getState()) return;

        if (this.mc.playerController == null || this.mc.playerController.isInCreativeMode()) return;

        if (this.mc.objectMouseOver == null || this.mc.objectMouseOver.typeOfHit != MovingObjectPosition.MovingObjectType.BLOCK) return;

        AccessorPlayerControllerMP ctrl = (AccessorPlayerControllerMP) (Object) this.mc.playerController;

        int curDelay = ctrl.getBlockHitDelay();
        int confDelay = clampInt(getCfgInt("speedMineDelay", 0), 0, 4);
        int targetDelay = Math.min(curDelay, confDelay + 1);
        if (targetDelay != curDelay) ctrl.setBlockHitDelay(targetDelay);

        if (ctrl.getIsHittingBlock()) {
            float cur = ctrl.getCurBlockDamageMP();
            int pct = clampInt(getCfgInt("speedMineSpeed", 60), 5, 100);
            pct -= (pct % 5);
            float percent = pct / 100.0F; // 0..1
            float base = 0.3F * percent;
            if (cur < base) ctrl.setCurBlockDamageMP(base);
        }
    }

    private static int clampInt(int v, int lo, int hi) { return v < lo ? lo : (v > hi ? hi : v); }

    private static int getCfgInt(String field, int def) {
        try {
            java.lang.reflect.Field f = cfg.v.getClass().getField(field);
            Object val = f.get(cfg.v);
            if (val instanceof Number) return ((Number) val).intValue();
            if (val != null) return Integer.parseInt(String.valueOf(val));
            return def;
        } catch (Throwable t) { return def; }
    }

}
