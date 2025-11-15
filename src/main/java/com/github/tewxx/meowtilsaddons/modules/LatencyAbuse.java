package com.github.tewxx.meowtilsaddons.modules;

import net.minecraft.client.Minecraft;
import net.minecraft.client.network.NetHandlerPlayClient;
import net.minecraft.network.play.server.S12PacketEntityVelocity;
import net.minecraft.network.play.server.S27PacketExplosion;

import java.util.LinkedList;
import java.util.Queue;

public class LatencyAbuse {

    // Store intercepted packets here
    private static final Queue<S12PacketEntityVelocity> storedVelocity = new LinkedList<>();
    private static final Queue<S27PacketExplosion> storedExplosions = new LinkedList<>();

    // Toggle for enabling the module
    private static boolean enabled = false;

    public static void setEnabled(boolean flag) {
        enabled = flag;
    }

    public static boolean isEnabled() {
        return enabled;
    }

    // Called by your mixin
    public static boolean captureVelocityPacket(S12PacketEntityVelocity packet, NetHandlerPlayClient nh) {
        if (!enabled) return false;

        storedVelocity.add(packet);
        return true; // cancel it
    }

    // Called by your mixin
    public static boolean captureExplosionPacket(S27PacketExplosion packet, NetHandlerPlayClient nh) {
        if (!enabled) return false;

        storedExplosions.add(packet);
        return true; // cancel it
    }

    /**
     * Releases ALL stored packets instantly.
     * Call this whenever you want your “delayed damage / delayed kb” to apply.
     */
    public static void flush() {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.thePlayer == null || mc.theWorld == null) return;

        NetHandlerPlayClient nh = mc.thePlayer.sendQueue;

        while (!storedVelocity.isEmpty()) {
            S12PacketEntityVelocity vel = storedVelocity.poll();
            nh.handleEntityVelocity(vel);
        }

        while (!storedExplosions.isEmpty()) {
            S27PacketExplosion ex = storedExplosions.poll();
            nh.handleExplosion(ex);
        }
    }

    /** Clears all stored packets without applying them */
    public static void purge() {
        storedVelocity.clear();
        storedExplosions.clear();
    }
}
