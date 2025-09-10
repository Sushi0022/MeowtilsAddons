package com.github.tewxx.meowtilsaddons.mixin.net;

import net.minecraft.client.network.NetHandlerPlayClient;
import net.minecraft.network.Packet;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.lang.reflect.Method;
import java.util.Collection;

@Pseudo
@Mixin(value = NetHandlerPlayClient.class, remap = false)
public abstract class MixinNetHandlerPlayClient {

    @Inject(method = "addToSendQueue", at = @At("HEAD"))
    private void onAddToSendQueue(Packet packet, CallbackInfo ci) {
        try {
            Collection<Object> values = com.github.tewxx.meowtilsaddons.InjectionState.latestInjectedModuleInstances.values();
            if (values == null || values.isEmpty()) return;
            for (Object module : values) {
                if (module == null) continue;
                try {
                    Method m = module.getClass().getMethod("onPacketSent", Object.class);
                    m.invoke(module, packet);
                } catch (NoSuchMethodException ignore) {
                    // module does not implement onPacketSent; skip
                } catch (Throwable t) {
                    // swallow per-module errors to avoid breaking networking
                }
            }
        } catch (Throwable ignoredOuter) {
        }
    }
}
