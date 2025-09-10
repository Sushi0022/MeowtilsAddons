package com.example.mixin.meowtils;

import net.minecraft.util.EnumChatFormatting;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import com.example.InjectionState;

/**
 * Fallback hook: intercept Meowtils.addCleanMessage directly.
 * This will confirm the target class name and allow us to inject
 * our custom line immediately after the specific message regardless
 * of where it is called from.
 */
@Pseudo
@Mixin(targets = "wtf.tatp.meowtils.Meowtils", remap = false)
public class MixinMeowtilsRoot {

    private static final ThreadLocal<Boolean> reentry = ThreadLocal.withInitial(() -> Boolean.FALSE);

    static {
        System.out.println("[MeowtilsInjectors][DEBUG] MixinMeowtilsRoot class loaded.");
    }

    @Inject(method = "addCleanMessage(Ljava/lang/String;)Ljava/lang/String;", at = @At("TAIL"))
    private static void onAddCleanMessageTail(String message, CallbackInfoReturnable<String> cir) {
        // Debug what Meowtils is actually printing
        System.out.println("[MeowtilsInjectors][DEBUG][Root] addCleanMessage TAIL | msg='" + message + "' | enabled=" + InjectionState.meowtilsInjectEnabled + ", reentry=" + reentry.get());

        if (Boolean.TRUE.equals(reentry.get())) return; // prevent recursion on our own call

        boolean isAutoTextClear = message != null && (message.contains("/autotext<1-10> clear") || message.contains("autotext<1-10> clear"));
        if (InjectionState.meowtilsInjectEnabled && isAutoTextClear) {
            try {
                reentry.set(Boolean.TRUE);
                // Inject our line immediately AFTER the clear line (TAIL runs after original execution)
                Class<?> meowtils = Class.forName("wtf.tatp.meowtils.Meowtils", true, MixinMeowtilsRoot.class.getClassLoader());
                java.lang.reflect.Method m = meowtils.getDeclaredMethod("addCleanMessage", String.class);
                m.setAccessible(true);
                System.out.println("[MeowtilsInjectors][DEBUG][Root] Injecting custom lines after autotext clear line (TAIL). Count=" + InjectionState.meowtilsInjectMessages.size());
                synchronized (InjectionState.meowtilsInjectMessages) {
                    for (String line : InjectionState.meowtilsInjectMessages) {
                        m.invoke(null, EnumChatFormatting.GREEN + line);
                    }
                }
            } catch (Throwable t) {
                System.out.println("[MeowtilsInjectors][DEBUG][Root] Injection via root hook failed: " + t);
                t.printStackTrace();
            } finally {
                reentry.set(Boolean.FALSE);
            }
        }
    }
}
