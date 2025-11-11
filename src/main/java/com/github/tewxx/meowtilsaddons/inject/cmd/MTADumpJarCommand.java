package com.github.tewxx.meowtilsaddons.inject.cmd;

import com.github.tewxx.meowtilsaddons.inject.JarDumper;
import net.minecraft.client.Minecraft;
import net.minecraft.command.CommandBase;
import net.minecraft.command.ICommandSender;
import net.minecraft.util.ChatComponentText;

public class MTADumpJarCommand extends CommandBase {

    @Override
    public String getCommandName() {
        return "mta_dumpjar";
    }

    @Override
    public String getCommandUsage(ICommandSender sender) {
        return "/mta_dumpjar [tag] - dump Meowtils jar with transformed classes to Downloads/MeowtilsAddons-debug";
    }

    @Override
    public void processCommand(ICommandSender sender, String[] args) {
        String tag = (args != null && args.length > 0) ? args[0] : "patched";
        String out = JarDumper.dumpMeowtilsWithTransformed(tag);
        if (out != null) {
            send("Patched jar written: " + out);
        } else {
            send("Dump failed. Ensure mixin.debug.export=true and try again.");
        }
    }

    private void send(String msg) {
        try {
            Minecraft.getMinecraft().thePlayer.addChatMessage(new ChatComponentText("[MTA] " + msg));
        } catch (Throwable ignored) {}
    }

    @Override
    public int getRequiredPermissionLevel() {
        return 0;
    }
}
