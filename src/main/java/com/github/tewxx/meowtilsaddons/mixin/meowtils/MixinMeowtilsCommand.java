package com.github.tewxx.meowtilsaddons.mixin.meowtils;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.util.EnumChatFormatting;
import wtf.tatp.meowtils.Meowtils;
import wtf.tatp.meowtils.commands.meowtils.MeowtilsCommand;

@Mixin(value = MeowtilsCommand.class, remap = false)
public class MixinMeowtilsCommand {
    @Inject(method = "sendPageContent(I)V", at = @At("TAIL"))
    private void meowtilsaddons$appendCommands(int page, CallbackInfo ci) {
        if (page == 4) {
            String sep = " \u00bb ";
            Meowtils.addCleanMessage(EnumChatFormatting.GREEN + "/bedwarslevel <1-5000>" +
                    EnumChatFormatting.YELLOW + sep + EnumChatFormatting.DARK_GRAY + "Set Bedwars level slider");
            Meowtils.addCleanMessage(EnumChatFormatting.GREEN + "/networklevel <1-500>" +
                    EnumChatFormatting.YELLOW + sep + EnumChatFormatting.DARK_GRAY + "Set Network level slider");
            Meowtils.addCleanMessage(EnumChatFormatting.GREEN + "/insults" +
                    EnumChatFormatting.YELLOW + sep + EnumChatFormatting.DARK_GRAY + "Opens killinsults.txt to add your own insults");
        }
    }
}
