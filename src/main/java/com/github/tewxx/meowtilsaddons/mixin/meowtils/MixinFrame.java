package com.github.tewxx.meowtilsaddons.mixin.meowtils;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import wtf.tatp.meowtils.gui.Module;
import wtf.tatp.meowtils.config.cfg;

/**
 * Adds config persistence for the dynamically-injected Category.Rejects in Frame.loadFromConfig/saveToConfig.
 */
@Pseudo
@Mixin(targets = "wtf.tatp.meowtils.gui.component.Frame", remap = false)
public abstract class MixinFrame {

    @Shadow public Module.Category category;
    @Shadow private int x;
    @Shadow private int y;
    @Shadow public boolean open;

    @Inject(method = "loadFromConfig", at = @At("HEAD"), cancellable = true)
    private void meowtilsaddons$loadRejects(CallbackInfo ci) {
        try {
            if (this.category != null && "Rejects".equals(this.category.name())) {
                try {
                    java.lang.reflect.Field fx = cfg.class.getField("rejectsCategoryX");
                    java.lang.reflect.Field fy = cfg.class.getField("rejectsCategoryY");
                    java.lang.reflect.Field fe = cfg.class.getField("rejectsCategoryExpanded");
                    this.x = fx.getInt(cfg.v);
                    this.y = fy.getInt(cfg.v);
                    this.open = fe.getBoolean(cfg.v);
                    ci.cancel();
                } catch (Throwable reflectMissing) {
                    // Fields not present; fall through to original switch/default handling
                }
            }
        } catch (Throwable ignored) { }
    }

    @Inject(method = "saveToConfig", at = @At("HEAD"), cancellable = true)
    private void meowtilsaddons$saveRejects(CallbackInfo ci) {
        try {
            if (this.category != null && "Rejects".equals(this.category.name())) {
                try {
                    java.lang.reflect.Field fx = cfg.class.getField("rejectsCategoryX");
                    java.lang.reflect.Field fy = cfg.class.getField("rejectsCategoryY");
                    java.lang.reflect.Field fe = cfg.class.getField("rejectsCategoryExpanded");
                    fx.setInt(cfg.v, this.x);
                    fy.setInt(cfg.v, this.y);
                    fe.setBoolean(cfg.v, this.open);
                    cfg.save();
                    ci.cancel();
                } catch (Throwable reflectMissing) {
                    // Fields not present; allow original code to handle persistence (default path)
                }
            }
        } catch (Throwable ignored) { }
    }
}
