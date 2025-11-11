package com.github.tewxx.meowtilsaddons.mixin.minecraft;

/**
 * Unified accessor implemented by both PlayerControllerMP and PlayerControllerOF
 * via mixins, so modules can cast without worrying about OptiFine.
 */
public interface ControllerAccessor {
    int getBlockHitDelay();
    void setBlockHitDelay(int value);

    boolean getIsHittingBlock();

    float getCurBlockDamageMP();
    void setCurBlockDamageMP(float value);
}
