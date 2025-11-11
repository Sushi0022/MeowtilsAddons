package com.github.tewxx.meowtilsaddons.inject.cmd;

import com.github.tewxx.meowtilsaddons.inject.JarDumper;
import net.minecraft.client.Minecraft;
import net.minecraft.command.CommandBase;
import net.minecraft.command.ICommandSender;
import net.minecraft.util.ChatComponentText;

public class MTADumpJarPathCommand extends CommandBase {

    @Override
    public String getCommandName() {
        return "mta_dumpjarpath";
    }

    @Override
    public String getCommandUsage(ICommandSender sender) {
        return "/mta_dumpjarpath <exportDir> [tag] - use a custom mixin export dir (where .class files are)";
    }

    @Override
    public void processCommand(ICommandSender sender, String[] args) {
        if (args == null || args.length == 0) {
            send("Usage: /mta_dumpjarpath <exportDir> [tag]");
            return;
        }
        String exportDir = args[0];
        String tag = args.length > 1 ? args[1] : "patched";
        String out = JarDumper.dumpMeowtilsWithTransformed(tag, exportDir);
        if (out != null) {
            send("Patched jar written: " + out);
        } else {
            send("Dump failed. Dir invalid or no transformed classes found.");
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
