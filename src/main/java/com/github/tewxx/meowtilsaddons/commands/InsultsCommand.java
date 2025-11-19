package com.github.tewxx.meowtilsaddons.commands;

import net.minecraft.client.Minecraft;
import net.minecraft.command.CommandBase;
import net.minecraft.command.ICommandSender;
import net.minecraft.util.EnumChatFormatting;
import wtf.tatp.meowtils.Meowtils;

import java.awt.Desktop;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;

public class InsultsCommand extends CommandBase {
    @Override
    public String getCommandName() {
        return "insults";
    }

    @Override
    public String getCommandUsage(ICommandSender sender) {
        return "/insults";
    }

    @Override
    public void processCommand(ICommandSender sender, String[] args) {
        try {
            File mcDir = Minecraft.getMinecraft().mcDataDir;
            File cfgDir = new File(mcDir, "config/MeowtilsAddons");
            if (!cfgDir.exists()) cfgDir.mkdirs();
            File txt = new File(cfgDir, "killinsults.txt");
            if (!txt.exists()) {
                writeDefaultTemplate(txt);
            }
            boolean opened = tryOpen(txt);
            if (opened) {
                send("Opened: " + txt.getAbsolutePath());
            } else {
                send("Edit this file: " + txt.getAbsolutePath());
            }
        } catch (Throwable t) {
            send("Failed to open insults file: " + t.getClass().getSimpleName());
        }
    }

    private boolean tryOpen(File f) {
        try {
            if (Desktop.isDesktopSupported()) {
                Desktop desk = Desktop.getDesktop();
                try { desk.edit(f); return true; } catch (Throwable ignored) {}
                try { desk.open(f); return true; } catch (Throwable ignored) {}
            }
        } catch (Throwable ignored) {}
        return false;
    }

    private void writeDefaultTemplate(File f) {
        try (BufferedWriter bw = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(f), StandardCharsets.UTF_8))) {
            bw.write("# MeowtilsAddons KillInsults\n");
            bw.write("# One line per insult.\n");
            bw.write("# Use {n} to insert the victim's name.\n");
            bw.write("gg ez\n");
            bw.write("sit down\n");
            bw.write("too easy\n");
            bw.flush();
        } catch (Throwable ignored) {}
    }

    private void send(String msg) {
        try { Meowtils.addMessage(EnumChatFormatting.GREEN + msg); } catch (Throwable ignored) {}
    }

    @Override
    public int getRequiredPermissionLevel() { return 0; }
}
