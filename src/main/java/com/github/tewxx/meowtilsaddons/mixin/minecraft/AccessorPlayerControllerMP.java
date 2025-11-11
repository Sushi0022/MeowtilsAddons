package com.github.tewxx.meowtilsaddons.mixin.minecraft;

import net.minecraft.client.multiplayer.PlayerControllerMP;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.gen.Accessor;

@Pseudo
@Mixin(value = PlayerControllerMP.class, remap = false)
public interface AccessorPlayerControllerMP {
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
