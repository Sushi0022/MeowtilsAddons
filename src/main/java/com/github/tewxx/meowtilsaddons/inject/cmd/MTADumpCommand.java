package com.github.tewxx.meowtilsaddons.inject.cmd;

import com.github.tewxx.meowtilsaddons.inject.ModuleInjector;
import net.minecraft.client.Minecraft;
import net.minecraft.command.CommandBase;
import net.minecraft.command.ICommandSender;
import net.minecraft.util.ChatComponentText;

public class MTADumpCommand extends CommandBase {

    @Override
    public String getCommandName() {
        return "mta_dump";
    }

    @Override
    public String getCommandUsage(ICommandSender sender) {
        return "/mta_dump [tag] - dump Meowtils modules to Downloads/MeowtilsAddons-debug";
    }

    @Override
    public void processCommand(ICommandSender sender, String[] args) {
        String tag = args != null && args.length > 0 ? args[0] : "manual";
        String path = ModuleInjector.dumpNow(tag);
        if (path != null) {
            send("Dump written: " + path);
        } else {
            send("Dump failed. Check logs for details.");
        }
    }

    private void send(String msg) {
        try {
            Minecraft.getMinecraft().thePlayer.addChatMessage(new ChatComponentText("[MTA] " + msg));
        } catch (Throwable ignored) { }
    }

    @Override
    public int getRequiredPermissionLevel() {
        return 0; // allow all clients
    }
}
