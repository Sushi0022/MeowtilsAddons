package com.github.tewxx.meowtilsaddons.commands;

import net.minecraft.command.CommandBase;
import net.minecraft.command.ICommandSender;
import net.minecraft.util.EnumChatFormatting;
import wtf.tatp.meowtils.Meowtils;
import wtf.tatp.meowtils.config.cfg;
import com.github.tewxx.meowtilsaddons.modules.LevelFaker;

public class BedwarsLevelCommand extends CommandBase {
    @Override
    public String getCommandName() {
        return "bedwarslevel";
    }

    @Override
    public String getCommandUsage(ICommandSender sender) {
        return "/bedwarslevel <1-5000>";
    }

    @Override
    public void processCommand(ICommandSender sender, String[] args) {
        if (args.length < 1) {
            send("Usage: /bedwarslevel <1-5000>");
            return;
        }
        try {
            int v = Integer.parseInt(args[0]);
            if (v < 1) v = 1; if (v > 5000) v = 5000;
            // Update cfg and GUI slider via LevelFaker helper
            LevelFaker.setBedwarsLevelExternal(v);
            send("Bedwars Level set to " + v);
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
