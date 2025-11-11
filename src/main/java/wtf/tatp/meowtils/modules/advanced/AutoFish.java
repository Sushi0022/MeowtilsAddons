package wtf.tatp.meowtils.modules.advanced;

import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import wtf.tatp.meowtils.gui.Module;
import net.minecraft.item.ItemFishingRod;
import net.minecraft.item.ItemStack;
import net.minecraft.entity.projectile.EntityFishHook;
import com.github.tewxx.meowtilsaddons.mixin.minecraft.AccessorEntityFishHook;
import net.minecraftforge.client.event.sound.PlaySoundEvent;
import net.minecraft.util.ChatComponentText;

/**
 * Empty AutoFish module placed under Meowtils namespace so ModuleManager
 * can reference it directly via mixin. It is intentionally minimal and
 * only exists to appear in the GUI and for config persistence tests.
 */
public class AutoFish extends Module {
    // Simple state/cooldowns
    private int tick;
    private int nextActionTick;
    private double prevHookY;
    private int biteWindowTicks; // legacy window from motion heuristic (no longer directly reels)
    private int hookAgeTicks; // ticks since current bobber spawned
    private int noBiteTimeout; // ticks since last bite window
    private int lastCatchable; // previous tick catchable value
    private boolean didReelThisCast; // prevent multiple reels per cast
    private int lastReelTick;
    private int lastUseTick; // guard against double-use in same moment
    private boolean wasHooked; // started catch delay
    private int catchDelayTicks; // countdown before reeling
    private boolean lastHookedEntity; // previous tick hooked-entity state
    private double prevDy; // previous-frame vertical delta for bob detection
    private int lastChatTick; // throttle chat debug
    // TEMP: print-all-sounds debug state (disable now that we have the key)
    private boolean debugAllSounds = false;
    private int chatCountTick = -1;
    private int chatCount = 0; // per-tick cap

    public AutoFish() {
        super("AutoFish", "autoFishKey", "autoFish", Module.Category.Advanced);
        try { this.tooltip("Lobby Fishing Bot\n§cWarning: No failsafes for staff checks."); } catch (Throwable ignored) {}
    }

    // Optional accessors (not required for bob detection)
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

        tick++;

        // Respect a small cooldown between actions
        if (tick < nextActionTick) return;

        // Require holding a fishing rod (keeps behavior predictable)
        if (!isHoldingRod()) return;

        // Player's current bobber (null if not cast)
        EntityFishHook hook = this.mc.thePlayer.fishEntity;

        if (hook == null || hook.isDead) {
            // Not fishing: cast the rod
            // Avoid immediate recast after a reel
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

        // When bobber exists, detect a bite via simple 'bob' heuristic
        hookAgeTicks++;
        noBiteTimeout++;
        double y = hook.posY;

        // Simple "bob" detection: strong downward spike after settle time while in water
        // We intentionally ignore accessors here per user's request

        if (Double.isNaN(prevHookY)) {
            // First tick seeing this hook: initialize baseline and wait next tick
            prevHookY = y;
            prevDy = 0.0;
            return;
        } else {
            double dy = y - prevHookY; // per tick delta
            boolean inWater = hook.isInWater();
            // Require a small settle time after casting to avoid false positives
            if (inWater && hookAgeTicks > 15 && !didReelThisCast && !wasHooked) {
                // Prefer motionY when available for smoother signal
                try { dy = hook.motionY; } catch (Throwable t2) { /* keep computed dy */ }
                // Rising-edge: previously near-still or moving up slightly, now a sharp drop
                boolean spike = (prevDy > -0.005) && (dy < -0.12);
                if (spike) {
                    wasHooked = true;
                    catchDelayTicks = 4; // short delay before reel
                    noBiteTimeout = 0;
                    // no console spam
                }
                prevDy = dy;
            }
        }
        prevHookY = y;

        if (biteWindowTicks > 0) biteWindowTicks--; // maintained for debugging; not used to reel directly

        // If we have an active catch delay, count it down and reel once
        if (wasHooked) {
            if (catchDelayTicks > 0) {
                catchDelayTicks--;
            } else if (!didReelThisCast && (tick - lastReelTick) > 16) {
                useRod();
                nextActionTick = tick + 18;
                didReelThisCast = true;
                wasHooked = false;
                lastReelTick = tick;
                // no console spam
            }
        }

        // If the bobber failed to land in water soon after casting, reel and try again
        // Disabled fail-cast recast path to prevent loops on shallow water/laggy servers.

        // Safety auto-recast disabled to avoid loops; only react to bob spikes
    }

    // Sound-driven bite detection: reel shortly after a nearby "ding/pling" sound
    @SubscribeEvent
    public void onPlaySound(PlaySoundEvent event) {
        try {
            if (!this.getState()) return;
            if (this.mc == null || this.mc.thePlayer == null) return;
            if (!isHoldingRod()) return;
            EntityFishHook hook = this.mc.thePlayer.fishEntity;
            if (hook == null || hook.isDead) return;

            String name = event.name;
            if (name == null) return;
            String n = name.toLowerCase(java.util.Locale.ROOT);

            // Check distance: only react if sound is near the bobber
            double sx = event.sound.getXPosF();
            double sy = event.sound.getYPosF();
            double sz = event.sound.getZPosF();
            double dx = hook.posX - sx;
            double dy = hook.posY - sy;
            double dz = hook.posZ - sz;
            double distSq = dx*dx + dy*dy + dz*dz;

            // TEMP DEBUG: print every sound within ~16 blocks to chat, capped per tick
            if (debugAllSounds && distSq <= 256.0) {
                if (chatCountTick != tick) { chatCountTick = tick; chatCount = 0; }
                if (chatCount < 4) { // cap 4 lines per tick
                    try { this.mc.thePlayer.addChatMessage(new ChatComponentText("§7[§9AutoFish§7] §8sound=§f" + name + " §8d2=§f" + String.format(java.util.Locale.ROOT, "%.2f", distSq))); } catch (Throwable ignored) {}
                    chatCount++;
                }
            }
            // Ignore very common water sounds for bite trigger
            if (n.contains("swim") || n.contains("splash") || n.contains("water")) return;

            // no console debug to avoid GL spam overlap
            // Allow up to ~22 blocks (d2 <= 484) per user's environment
            if (distSq > 484.0) return;

            // Common bite chime keys (1.8): random.orb (xp), note.* family, or explicit pling
            boolean match = n.contains("random.orb") || n.startsWith("note.") || n.contains("pling");
            if (!match) return;

            // Require hook to be in water and some settle time
            if (!hook.isInWater() || hookAgeTicks < 8) return;

            // For exact bite chime 'note.pling', reel immediately once per cast
            if (!didReelThisCast && (tick - lastReelTick) > 10 && n.equals("note.pling")) {
                useRod();
                nextActionTick = tick + 18;
                didReelThisCast = true;
                wasHooked = false;
                lastReelTick = tick;
                return;
            }

            // Otherwise, start a short catch window; main tick loop will reel once
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
            // Prefer standard controller path to simulate right-click use
            ItemStack held = this.mc.thePlayer.getHeldItem();
            if (held != null) {
                // Prevent double-use spam: minimal interval between uses
                if ((tick - lastUseTick) < 5) return;
                this.mc.playerController.sendUseItem(this.mc.thePlayer, this.mc.theWorld, held);
                lastUseTick = tick;
                return;
            }
        } catch (Throwable ignored) {}
    }
}
