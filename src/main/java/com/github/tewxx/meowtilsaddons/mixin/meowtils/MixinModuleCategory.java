package com.github.tewxx.meowtilsaddons.mixin.meowtils;

import net.minecraftforge.common.util.EnumHelper;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(targets = "wtf.tatp.meowtils.gui.Module$Category", remap = false)
public abstract class MixinModuleCategory {

    @Inject(method = "<clinit>", at = @At("RETURN"))
    private static void meowtilsaddons$addRejects(CallbackInfo ci) {
        try {
            Class<?> enumCls = Class.forName("wtf.tatp.meowtils.gui.Module$Category");
            try {
                java.lang.Enum.valueOf((Class) enumCls, "Rejects");
                return;
            } catch (IllegalArgumentException ignored) { }
            EnumHelper.addEnum((Class) enumCls, "Rejects", new Class<?>[]{}, new Object[]{});
            System.out.println("[MeowtilsAddons] Added Module.Category.Rejects at runtime");
        } catch (Throwable t) {
            System.out.println("[MeowtilsAddons] Failed to add Module.Category.Rejects: " + t);
        }
    }
}
