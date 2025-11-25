package com.github.tewxx.meowtilsaddons.modules;

import net.minecraft.client.Minecraft;
import net.minecraft.command.CommandBase;
import net.minecraft.command.ICommandSender;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.IChatComponent;
import net.minecraftforge.client.ClientCommandHandler;
import net.minecraftforge.client.event.ClientChatReceivedEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import wtf.tatp.meowtils.gui.Module;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

public class NameSpoofer extends Module {

    private static NameSpoofer INSTANCE;
    
    // Default if config fails
    private String currentSpoofName = "SpoofedName"; 

    public NameSpoofer() {
        super("NameSpoofer", "nameSpooferKey", "nameSpoofer", Module.Category.Render);
        INSTANCE = this;
        
        // Register commands with updated names
        ClientCommandHandler.instance.registerCommand(new SetSpoofNameCommand());
        ClientCommandHandler.instance.registerCommand(new ResetSpoofNameCommand());
        
        try { 
            this.tooltip("Replaces your username. Use /spoofname <name> or /resetname."); 
        } catch (Throwable ignored) {}
    }

    @Override
    public void onEnable() {
        super.onEnable();
        MinecraftForge.EVENT_BUS.register(this);
        updateSpoofName(); // Load from config when enabled
    }

    @Override
    public void onDisable() {
        super.onDisable();
        MinecraftForge.EVENT_BUS.unregister(this);
    }

    @SubscribeEvent
    public void onChatReceived(ClientChatReceivedEvent event) {
        if (!this.getState()) return;

        Minecraft mc = Minecraft.getMinecraft();
        if (mc.thePlayer == null) return;

        String realName = mc.thePlayer.getName();
        String originalMessage = event.message.getFormattedText();

        updateSpoofName();

        if (!currentSpoofName.equals(realName) && originalMessage.contains(realName)) {
            String newMessageText = originalMessage.replace(realName, currentSpoofName);
            event.message = new ChatComponentText(newMessageText);
        }
    }
    
    private void updateSpoofName() {
        try {
            Class<?> cfgClass = Class.forName("wtf.tatp.meowtils.config.cfg");
            Field vField = cfgClass.getDeclaredField("v");
            vField.setAccessible(true);
            Object cfgInstance = vField.get(null);
            
            if (cfgInstance != null) {
                Field nameField = cfgClass.getDeclaredField("spoofedName");
                nameField.setAccessible(true);
                Object val = nameField.get(cfgInstance);
                if (val != null) {
                    currentSpoofName = (String) val;
                }
            }
        } catch (Exception e) {}
    }
    
    public void setAndSaveSpoofName(String newName) {
        try {
            Class<?> cfgClass = Class.forName("wtf.tatp.meowtils.config.cfg");
            Field vField = cfgClass.getDeclaredField("v");
            vField.setAccessible(true);
            Object cfgInstance = vField.get(null);
            
            if (cfgInstance != null) {
                Field nameField = cfgClass.getDeclaredField("spoofedName");
                nameField.setAccessible(true);
                nameField.set(cfgInstance, newName);
                
                this.currentSpoofName = newName;
                
                Method saveMethod = cfgClass.getDeclaredMethod("save");
                saveMethod.setAccessible(true);
                saveMethod.invoke(null);
            }
        } catch (Exception e) {
            Minecraft.getMinecraft().thePlayer.addChatMessage(new ChatComponentText(EnumChatFormatting.RED + "[NameSpoofer] Failed to save config: " + e.getMessage()));
        }
    }

    // --- COMMAND: /spoofname ---
    private static class SetSpoofNameCommand extends CommandBase {
        @Override public String getCommandName() { return "spoofname"; }
        @Override public String getCommandUsage(ICommandSender sender) { return "/spoofname <name>"; }
        @Override public int getRequiredPermissionLevel() { return 0; }
        @Override public boolean canCommandSenderUseCommand(ICommandSender sender) { return true; }

        @Override
        public void processCommand(ICommandSender sender, String[] args) {
            if (INSTANCE == null) return;

            if (args.length == 0) {
                sender.addChatMessage(new ChatComponentText(EnumChatFormatting.RED + "Usage: /spoofname <name>"));
                return;
            }

            String newName = String.join(" ", args);
            INSTANCE.setAndSaveSpoofName(newName);
            
            String coloredName = newName.replaceAll("&", "\u00a7");
            sender.addChatMessage(new ChatComponentText(EnumChatFormatting.GREEN + "[NameSpoofer] Name set to: " + EnumChatFormatting.RESET + coloredName));
        }
    }
    
    // --- COMMAND: /resetname ---
    private static class ResetSpoofNameCommand extends CommandBase {
        @Override public String getCommandName() { return "resetname"; }
        @Override public String getCommandUsage(ICommandSender sender) { return "/resetname"; }
        @Override public int getRequiredPermissionLevel() { return 0; }
        @Override public boolean canCommandSenderUseCommand(ICommandSender sender) { return true; }

        @Override
        public void processCommand(ICommandSender sender, String[] args) {
            if (INSTANCE == null) return;
            
            Minecraft mc = Minecraft.getMinecraft();
            if (mc.thePlayer == null) return;

            String realName = mc.thePlayer.getName();
            INSTANCE.setAndSaveSpoofName(realName);
            
            sender.addChatMessage(new ChatComponentText(EnumChatFormatting.GREEN + "[NameSpoofer] Name reset to original: " + realName));
        }
    }
}