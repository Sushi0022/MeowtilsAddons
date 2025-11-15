package com.github.tewxx.meowtilsaddons.mixin.minecraft;

import com.github.tewxx.meowtilsaddons.modules.LatencyAbuse;
import net.minecraft.client.network.NetHandlerPlayClient;
import net.minecraft.network.play.server.S12PacketEntityVelocity;
import net.minecraft.network.play.server.S27PacketExplosion;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(NetHandlerPlayClient.class)
public abstract class MixinNetHandlerPlayClient {
    @Inject(method = "handleEntityVelocity", at = @At("HEAD"), cancellable = true)
    private void meowtilsaddons$delayVelocity(S12PacketEntityVelocity packet, CallbackInfo ci) {
        if (LatencyAbuse.captureVelocityPacket(packet, (NetHandlerPlayClient) (Object) this)) {
            ci.cancel();
        }
    }

    @Inject(method = "handleExplosion", at = @At("HEAD"), cancellable = true)
    private void meowtilsaddons$delayExplosion(S27PacketExplosion packet, CallbackInfo ci) {
        if (LatencyAbuse.captureExplosionPacket(packet, (NetHandlerPlayClient) (Object) this)) {
            ci.cancel();
        }
    }
}
