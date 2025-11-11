package com.github.tewxx.meowtilsaddons.mixin.minecraft;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.gen.Accessor;

@Pseudo
@Mixin(targets = "net.optifine.override.PlayerControllerOF", remap = false)
public interface AccessorPlayerControllerOF extends ControllerAccessor {
    @Accessor("blockHitDelay")
    int getBlockHitDelay();

    @Accessor("blockHitDelay")
    void setBlockHitDelay(int value);

    @Accessor("isHittingBlock")
    boolean getIsHittingBlock();

    @Accessor("curBlockDamageMP")
    float getCurBlockDamageMP();

    @Accessor("curBlockDamageMP")
    void setCurBlockDamageMP(float value);
}
