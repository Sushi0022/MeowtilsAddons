package com.github.tewxx.meowtilsaddons.mixin.minecraft;

import com.github.tewxx.meowtilsaddons.modules.Freecam;
import net.minecraft.client.entity.EntityPlayerSP;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(EntityPlayerSP.class)
public abstract class MixinEntityPlayerSP {

    @Inject(method = "onUpdateWalkingPlayer", at = @At("HEAD"), cancellable = true)
    private void meowtilsaddons$freezeMovement(CallbackInfo ci) {
        if (Freecam.shouldSuppressMovement((EntityPlayerSP) (Object) this)) {
            ci.cancel();
        }
    }
}
