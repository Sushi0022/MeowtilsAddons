package com.github.tewxx.meowtilsaddons.mixin;

import net.minecraftforge.fml.common.Loader;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;
import org.spongepowered.asm.lib.tree.ClassNode;

import java.util.List;
import java.util.Set;

/**
 * Only enables our mixins when the target mod (by modid) is present at runtime.
 */
public class AddonsMixinPlugin implements IMixinConfigPlugin {
    private static final String TARGET_MODID = "meowtils";

    @Override
    public void onLoad(String mixinPackage) {
        // no-op
    }

    @Override
    public String getRefMapperConfig() {
        return null; // use default from config
    }

    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        // Gate all our mixins by modid
        return Loader.isModLoaded(TARGET_MODID);
    }

    @Override
    public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) {
        // no-op
    }

    @Override
    public List<String> getMixins() {
        return null; // use those defined in the JSON config
    }

    @Override
    public void preApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
        // no-op
    }

    @Override
    public void postApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
        // no-op
    }
}
