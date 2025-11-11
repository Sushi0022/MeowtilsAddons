package com.github.tewxx.meowtilsaddons.mixin.minecraft;

import net.minecraft.entity.Entity;
import net.minecraft.entity.projectile.EntityFishHook;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.gen.Accessor;

@Pseudo
@Mixin(value = EntityFishHook.class, remap = false)
public interface AccessorEntityFishHook {
    // MCP named fields in 1.8.9 deobf environment
    @Accessor("ticksCatchable")
    int getTicksCatchable();

    @Accessor("caughtEntity")
    Entity getCaughtEntity();
}
