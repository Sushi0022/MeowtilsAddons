package com.github.tewxx.meowtilsaddons.mixin.meowtils;

import net.minecraftforge.common.util.EnumHelper;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(targets = "wtf.tatp.meowtils.gui.Module$Modules", remap = false)
public abstract class MixinModuleModules {

    @Inject(method = "<clinit>", at = @At("RETURN"))
    private static void meowtilsaddons$addTest(CallbackInfo ci) {
        try {
            Class<?> enumCls = Class.forName("wtf.tatp.meowtils.gui.Module$Modules");
            try {
                java.lang.Enum.valueOf((Class) enumCls, "Test");
                return;
            } catch (IllegalArgumentException ignored) { }
            EnumHelper.addEnum((Class) enumCls, "Test", new Class<?>[]{}, new Object[]{});
            System.out.println("[MeowtilsAddons] Added Module.Modules.Test at runtime");
        } catch (Throwable t) {
            System.out.println("[MeowtilsAddons] Failed to add Module.Modules.Test: " + t);
        }
    }
}
