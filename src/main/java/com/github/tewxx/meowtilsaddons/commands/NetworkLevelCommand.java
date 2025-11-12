package com.github.tewxx.meowtilsaddons.commands;

import net.minecraft.command.CommandBase;
import net.minecraft.command.ICommandSender;
import net.minecraft.util.EnumChatFormatting;
import wtf.tatp.meowtils.Meowtils;
import wtf.tatp.meowtils.config.cfg;
import com.github.tewxx.meowtilsaddons.modules.LevelFaker;

public class NetworkLevelCommand extends CommandBase {
    @Override
    public String getCommandName() {
        return "networklevel";
    }

    @Override
    public String getCommandUsage(ICommandSender sender) {
        return "/networklevel <1-500>";
    }

    @Override
    public void processCommand(ICommandSender sender, String[] args) {
        if (args.length < 1) {
            send("Usage: /networklevel <1-500>");
            return;
        }
        try {
            int v = Integer.parseInt(args[0]);
            if (v < 1) v = 1; if (v > 500) v = 500;
            LevelFaker.setNetworkLevelExternal(v);
            send("Network Level set to " + v);
        } catch (NumberFormatException e) {
            send("Invalid number: " + args[0]);
        }
    }

    private void send(String msg) {
        try { Meowtils.addMessage(EnumChatFormatting.GREEN + msg); } catch (Throwable ignored) {}
    }

    @Override
    public int getRequiredPermissionLevel() { return 0; }

    private void setCfgInt(String field, int value) {
        try {
            java.lang.reflect.Field f = cfg.v.getClass().getField(field);
            f.setInt(cfg.v, value);
        } catch (Throwable ignored) {}
    }
}
