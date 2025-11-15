package com.github.tewxx.meowtilsaddons.mixin.minecraft;

import com.github.tewxx.meowtilsaddons.modules.Freecam;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Minecraft.class)
public abstract class MixinMinecraft {

    @Inject(method = "clickMouse", at = @At("HEAD"), cancellable = true)
    private void meowtilsaddons$cancelLeft(CallbackInfo ci) {
        if (Freecam.shouldBlockMouse()) {
            ci.cancel();
        }
    }

    @Inject(method = "rightClickMouse", at = @At("HEAD"), cancellable = true)
    private void meowtilsaddons$cancelRight(CallbackInfo ci) {
        if (Freecam.shouldBlockMouse()) {
            ci.cancel();
        }
    }
}
