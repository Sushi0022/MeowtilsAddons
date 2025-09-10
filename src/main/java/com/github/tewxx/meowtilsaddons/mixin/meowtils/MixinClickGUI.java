package com.example.mixin.meowtils;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

@Pseudo
@Mixin(targets = "wtf.tatp.meowtils.gui.ClickGUI", remap = false)
public class MixinClickGUI {

    // MCP name: initGui, Obf: func_73866_w_
    @Inject(method = "initGui", at = @At("HEAD"), require = 0)
    private void onInitHead(CallbackInfo ci) {
        try {
            Class<?> mm = Class.forName("wtf.tatp.meowtils.gui.ModuleManager");
            java.lang.reflect.Method gm = mm.getDeclaredMethod("getModules");
            List list = (List) gm.invoke(null);
            System.out.println("[MeowtilsInjectors][DEBUG] ClickGUI.initGui HEAD: modules=" + (list == null ? -1 : list.size()));
        } catch (Throwable t) {
            System.out.println("[MeowtilsInjectors][DEBUG] ClickGUI.initGui HEAD: failed to read modules: " + t);
        }
    }

    @Inject(method = "func_73866_w_", at = @At("HEAD"), require = 0)
    private void onInitHeadObf(CallbackInfo ci) {
        onInitHead(ci);
    }

    @Inject(method = "initGui", at = @At("RETURN"), require = 0)
    private void onInitReturn(CallbackInfo ci) {
        try {
            Class<?> mm = Class.forName("wtf.tatp.meowtils.gui.ModuleManager");
            java.lang.reflect.Method gm = mm.getDeclaredMethod("getModules");
            List list = (List) gm.invoke(null);
            System.out.println("[MeowtilsInjectors][DEBUG] ClickGUI.initGui RETURN: modules=" + (list == null ? -1 : list.size()));
        } catch (Throwable t) {
            System.out.println("[MeowtilsInjectors][DEBUG] ClickGUI.initGui RETURN: failed to read modules: " + t);
        }
    }

    @Inject(method = "func_73866_w_", at = @At("RETURN"), require = 0)
    private void onInitReturnObf(CallbackInfo ci) {
        onInitReturn(ci);
    }
}
