package com.example.mixin.meowtils;

import net.minecraft.util.EnumChatFormatting;
import net.minecraft.command.ICommandSender;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import com.example.InjectionState;

/**
 * Injects an extra chat line into Meowtils' /meow command output.
 */
@Pseudo
@Mixin(targets = "wtf.tatp.meowtils.commands.MeowtilsCommand", remap = false)
public class MixinMeowtilsCommand {

    static {
        System.out.println("[MeowtilsInjectors][DEBUG] MixinMeowtilsCommand class loaded. CL=" + MixinMeowtilsCommand.class.getClassLoader());
    }

    private static int debugPrinted = 0;
    private static int callCountObf = 0;
    private static int callCountMcp = 0;
    private static int injectCount = 0;

    // HEAD logs for both method name variants to confirm which one exists at runtime
    @Inject(method = "func_71515_b(Lnet/minecraft/command/ICommandSender;[Ljava/lang/String;)V", at = @At("HEAD"))
    private void onHeadObf(ICommandSender sender, String[] args, CallbackInfo ci) {
        System.out.println("[MeowtilsInjectors][DEBUG] onHead func_71515_b reached in MeowtilsCommand | sender=" + sender + ", argsLen=" + (args == null ? -1 : args.length));
        if (args != null) {
            for (int i = 0; i < args.length; i++) {
                System.out.println("[MeowtilsInjectors][DEBUG]   arg[" + i + "]='" + args[i] + "'");
            }
        }
    }

    @Inject(method = "processCommand(Lnet/minecraft/command/ICommandSender;[Ljava/lang/String;)V", at = @At("HEAD"))
    private void onHeadMcp(ICommandSender sender, String[] args, CallbackInfo ci) {
        System.out.println("[MeowtilsInjectors][DEBUG] onHead processCommand reached in MeowtilsCommand | sender=" + sender + ", argsLen=" + (args == null ? -1 : args.length));
        if (args != null) {
            for (int i = 0; i < args.length; i++) {
                System.out.println("[MeowtilsInjectors][DEBUG]   arg[" + i + "]='" + args[i] + "'");
            }
        }
    }

    @Inject(method = "func_71515_b(Lnet/minecraft/command/ICommandSender;[Ljava/lang/String;)V", at = @At("RETURN"))
    private void onReturnObf(ICommandSender sender, String[] args, CallbackInfo ci) {
        System.out.println("[MeowtilsInjectors][DEBUG] func_71515_b RETURN reached. Total calls intercepted so far: obf=" + callCountObf + ", mcp=" + callCountMcp + ", injects=" + injectCount);
    }

    @Inject(method = "processCommand(Lnet/minecraft/command/ICommandSender;[Ljava/lang/String;)V", at = @At("RETURN"))
    private void onReturnMcp(ICommandSender sender, String[] args, CallbackInfo ci) {
        System.out.println("[MeowtilsInjectors][DEBUG] processCommand RETURN reached. Total calls intercepted so far: obf=" + callCountObf + ", mcp=" + callCountMcp + ", injects=" + injectCount);
    }

    @Redirect(method = "func_71515_b(Lnet/minecraft/command/ICommandSender;[Ljava/lang/String;)V",
              at = @At(value = "INVOKE", target = "Lwtf/tatp/meowtils/Meowtils;addCleanMessage(Ljava/lang/String;)Ljava/lang/String;"),
              require = 0, expect = 0)
    private String redirectAddCleanMessageObf(String message) {
        callCountObf++;
        if (debugPrinted == 0) System.out.println("[MeowtilsInjectors][DEBUG] @Redirect hook is ACTIVE in func_71515_b.");

        if (debugPrinted < 100) {
            System.out.println("[MeowtilsInjectors][DEBUG] (obf) addCleanMessage intercepted: '" + String.valueOf(message) + "' | len=" + (message == null ? -1 : message.length()) + " | thread=" + Thread.currentThread().getName());
            debugPrinted++;
        }
        boolean isAutoTextClear = message != null && (message.contains("/autotext<1-10> clear") || message.contains("autotext<1-10> clear"));
        System.out.println("[MeowtilsInjectors][DEBUG] (obf) flags: enabled=" + InjectionState.meowtilsInjectEnabled + ", isAutoTextClear=" + isAutoTextClear);
        String originalRet = safeAddCleanMessage(message);
        if (InjectionState.meowtilsInjectEnabled && isAutoTextClear) {
            injectCount++;
            System.out.println("[MeowtilsInjectors][DEBUG] (obf) Injecting custom lines after autotext clear line. injectCount=" + injectCount + 
                    ", count=" + InjectionState.meowtilsInjectMessages.size());
            synchronized (InjectionState.meowtilsInjectMessages) {
                for (String line : InjectionState.meowtilsInjectMessages) {
                    safeAddCleanMessage(EnumChatFormatting.GREEN + line);
                }
            }
        }
        return originalRet;
    }

    @Redirect(method = "processCommand(Lnet/minecraft/command/ICommandSender;[Ljava/lang/String;)V",
              at = @At(value = "INVOKE", target = "Lwtf/tatp/meowtils/Meowtils;addCleanMessage(Ljava/lang/String;)Ljava/lang/String;"),
              require = 0, expect = 0)
    private String redirectAddCleanMessageMcp(String message) {
        callCountMcp++;
        if (debugPrinted == 0) System.out.println("[MeowtilsInjectors][DEBUG] @Redirect hook is ACTIVE in processCommand.");

        if (debugPrinted < 100) {
            System.out.println("[MeowtilsInjectors][DEBUG] (mcp) addCleanMessage intercepted: '" + String.valueOf(message) + "' | len=" + (message == null ? -1 : message.length()) + " | thread=" + Thread.currentThread().getName());
            debugPrinted++;
        }
        boolean isAutoTextClear = message != null && (message.contains("/autotext<1-10> clear") || message.contains("autotext<1-10> clear"));
        System.out.println("[MeowtilsInjectors][DEBUG] (mcp) flags: enabled=" + InjectionState.meowtilsInjectEnabled + ", isAutoTextClear=" + isAutoTextClear);
        String originalRet = safeAddCleanMessage(message);
        if (InjectionState.meowtilsInjectEnabled && isAutoTextClear) {
            injectCount++;
            System.out.println("[MeowtilsInjectors][DEBUG] (mcp) Injecting custom lines after autotext clear line. injectCount=" + injectCount + 
                    ", count=" + InjectionState.meowtilsInjectMessages.size());
            synchronized (InjectionState.meowtilsInjectMessages) {
                for (String line : InjectionState.meowtilsInjectMessages) {
                    safeAddCleanMessage(EnumChatFormatting.GREEN + line);
                }
            }
        }
        return originalRet;
    }

    private String safeAddCleanMessage(String msg) {
        try {
            System.out.println("[MeowtilsInjectors][DEBUG] safeAddCleanMessage start | msg='" + msg + "'");
            ClassLoader ourCl = MixinMeowtilsCommand.class.getClassLoader();
            System.out.println("[MeowtilsInjectors][DEBUG]   our CL=" + ourCl);
            Class<?> meowtils = Class.forName("wtf.tatp.meowtils.Meowtils", true, ourCl);
            System.out.println("[MeowtilsInjectors][DEBUG]   meowtils Class=" + meowtils + ", CL=" + meowtils.getClassLoader());
            java.lang.reflect.Method m = meowtils.getDeclaredMethod("addCleanMessage", String.class);
            m.setAccessible(true);
            Object ret = m.invoke(null, msg);
            System.out.println("[MeowtilsInjectors][DEBUG] safeAddCleanMessage success, return=" + ret);
            return ret == null ? null : String.valueOf(ret);
        } catch (Throwable t) {
            System.out.println("[MeowtilsInjectors][DEBUG] Reflection call to Meowtils.addCleanMessage failed: " + t);
            t.printStackTrace();
            return null;
        }
    }
}
