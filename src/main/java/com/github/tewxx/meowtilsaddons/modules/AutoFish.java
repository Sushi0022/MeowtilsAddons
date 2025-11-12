package com.github.tewxx.meowtilsaddons.modules;

import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import wtf.tatp.meowtils.gui.Module;
import net.minecraft.item.ItemFishingRod;
import net.minecraft.item.ItemStack;
import net.minecraft.entity.projectile.EntityFishHook;
import com.github.tewxx.meowtilsaddons.mixin.minecraft.AccessorEntityFishHook;
import net.minecraftforge.client.event.sound.PlaySoundEvent;
import net.minecraft.util.ChatComponentText;

public class AutoFish extends Module {
    private int tick;
    private int nextActionTick;
    private double prevHookY;
    private int biteWindowTicks;
    private int hookAgeTicks;
    private int noBiteTimeout;
    private int lastCatchable;
    private boolean didReelThisCast;
    private int lastReelTick;
    private int lastUseTick;
    private boolean wasHooked;
    private int catchDelayTicks;
    private boolean lastHookedEntity;
    private double prevDy;
    private int lastChatTick;
    private boolean debugAllSounds = false;
    private int chatCountTick = -1;
    private int chatCount = 0;

    public AutoFish() {
        super("AutoFish", "autoFishKey", "autoFish", Module.Category.Advanced);
        try { this.tooltip("Lobby Fishing Bot\n§cWarning: No failsafes for staff checks."); } catch (Throwable ignored) {}
    }

    private int getCatchableTicks(EntityFishHook hook) {
        try { return ((AccessorEntityFishHook) (Object) hook).getTicksCatchable(); } catch (Throwable t) { return 0; }
    }
    private boolean hasHookedEntity(EntityFishHook hook) {
        try { return ((AccessorEntityFishHook) (Object) hook).getCaughtEntity() != null; } catch (Throwable t) { return false; }
    }

    @Override
    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (this.mc == null || this.mc.thePlayer == null || this.mc.theWorld == null) return;
        if (event.phase != TickEvent.Phase.START) return;
        if (!this.getState()) return;
        if (this.mc.currentScreen != null) return;

        tick++;

        if (tick < nextActionTick) return;

        if (!isHoldingRod()) return;

        EntityFishHook hook = this.mc.thePlayer.fishEntity;

        if (hook == null || hook.isDead) {
            if ((tick - lastReelTick) < 14) return;
            useRod();
            nextActionTick = tick + 12; // small delay before next check/action
            prevHookY = Double.NaN;
            biteWindowTicks = 0;
            hookAgeTicks = 0;
            noBiteTimeout = 0;
            lastCatchable = 0;
            didReelThisCast = false;
            wasHooked = false;
            catchDelayTicks = 0;
            lastHookedEntity = false;
            prevDy = 0.0;
            return;
        }

        hookAgeTicks++;
        noBiteTimeout++;
        double y = hook.posY;


        if (Double.isNaN(prevHookY)) {
            prevHookY = y;
            prevDy = 0.0;
            return;
        } else {
            double dy = y - prevHookY; // per tick delta
            boolean inWater = hook.isInWater();
            if (inWater && hookAgeTicks > 15 && !didReelThisCast && !wasHooked) {
                try { dy = hook.motionY; } catch (Throwable t2) { /* keep computed dy */ }
                boolean spike = (prevDy > -0.005) && (dy < -0.12);
                if (spike) {
                    wasHooked = true;
                    catchDelayTicks = 4; // short delay before reel
                    noBiteTimeout = 0;
                }
                prevDy = dy;
            }
        }
        prevHookY = y;

        if (biteWindowTicks > 0) biteWindowTicks--; // maintained for debugging; not used to reel directly

        if (wasHooked) {
            if (catchDelayTicks > 0) {
                catchDelayTicks--;
            } else if (!didReelThisCast && (tick - lastReelTick) > 16) {
                useRod();
                nextActionTick = tick + 18;
                didReelThisCast = true;
                wasHooked = false;
                lastReelTick = tick;
            }
        }
    }

    @SubscribeEvent
    public void onPlaySound(PlaySoundEvent event) {
        try {
            if (!this.getState()) return;
            if (this.mc == null || this.mc.thePlayer == null) return;
            if (this.mc.currentScreen != null) return;
            if (!isHoldingRod()) return;
            EntityFishHook hook = this.mc.thePlayer.fishEntity;
            if (hook == null || hook.isDead) return;

            String name = event.name;
            if (name == null) return;
            String n = name.toLowerCase(java.util.Locale.ROOT);

            double sx = event.sound.getXPosF();
            double sy = event.sound.getYPosF();
            double sz = event.sound.getZPosF();
            double dx = hook.posX - sx;
            double dy = hook.posY - sy;
            double dz = hook.posZ - sz;
            double distSq = dx*dx + dy*dy + dz*dz;

            if (debugAllSounds && distSq <= 256.0) {
                if (chatCountTick != tick) { chatCountTick = tick; chatCount = 0; }
                if (chatCount < 4) { // cap 4 lines per tick
                    try { this.mc.thePlayer.addChatMessage(new ChatComponentText("§7[§9AutoFish§7] §8sound=§f" + name + " §8d2=§f" + String.format(java.util.Locale.ROOT, "%.2f", distSq))); } catch (Throwable ignored) {}
                    chatCount++;
                }
            }
            if (n.contains("swim") || n.contains("splash") || n.contains("water")) return;

            if (distSq > 484.0) return;

            boolean match = n.contains("random.orb") || n.startsWith("note.") || n.contains("pling");
            if (!match) return;

            if (!hook.isInWater() || hookAgeTicks < 8) return;

            if (!didReelThisCast && (tick - lastReelTick) > 10 && n.equals("note.pling")) {
                useRod();
                nextActionTick = tick + 18;
                didReelThisCast = true;
                wasHooked = false;
                lastReelTick = tick;
                return;
            }

            if (!didReelThisCast && !wasHooked) {
                wasHooked = true;
                catchDelayTicks = 3;
                if ((tick - lastChatTick) > 40) {
                    try { this.mc.thePlayer.addChatMessage(new ChatComponentText("§7[§9AutoFish§7] sound: §f" + name)); } catch (Throwable ignored) {}
                    lastChatTick = tick;
                }
            }
        } catch (Throwable ignoredAll) {}
    }

    private boolean isHoldingRod() {
        ItemStack held = this.mc.thePlayer.getHeldItem();
        return held != null && held.getItem() instanceof ItemFishingRod;
    }

    private void useRod() {
        try {
            if (this.mc != null && this.mc.currentScreen != null) return;
            ItemStack held = this.mc.thePlayer.getHeldItem();
            if (held != null) {
                if ((tick - lastUseTick) < 5) return;
                this.mc.playerController.sendUseItem(this.mc.thePlayer, this.mc.theWorld, held);
                lastUseTick = tick;
            }
        } catch (Throwable ignored) {}
    }
}
