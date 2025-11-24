package com.github.tewxx.meowtilsaddons.modules;

import net.minecraft.client.Minecraft;
import net.minecraft.command.CommandBase;
import net.minecraft.command.ICommandSender;
import net.minecraft.event.ClickEvent;
import net.minecraft.util.BlockPos;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.EnumChatFormatting;
import net.minecraftforge.client.ClientCommandHandler;
import net.minecraftforge.client.event.ClientChatReceivedEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import wtf.tatp.meowtils.gui.Module;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class RankSpoofer extends Module {

    private static RankSpoofer INSTANCE;
    private String currentSpoofRank = "&c[ADMIN]"; 
    private String currentSpoofName = ""; 
    public static boolean debugMode = false;

    private static final Map<String, String> RANK_PRESETS = new HashMap<>();
    static {
        RANK_PRESETS.put("Default", "&7");
        RANK_PRESETS.put("VIP", "&a[VIP]");
        RANK_PRESETS.put("VIP+", "&a[VIP&6+&a]");
        RANK_PRESETS.put("MVP", "&b[MVP]");
        RANK_PRESETS.put("MVP+", "&b[MVP&c+&b]");
        RANK_PRESETS.put("MVP++", "&6[MVP&c++&6]");
        RANK_PRESETS.put("HELPER", "&9[HELPER]");
        RANK_PRESETS.put("MOD", "&2[MOD]");
        RANK_PRESETS.put("ADMIN", "&c[ADMIN]");
        RANK_PRESETS.put("OWNER", "&c[OWNER]");
        RANK_PRESETS.put("YOUTUBE", "&c[&fYOUTUBE&c]");
        RANK_PRESETS.put("SLOTH", "&c[SLOTH]");
        RANK_PRESETS.put("MOJANG", "&6[MOJANG]");
        RANK_PRESETS.put("APPLE", "&6[APPLE]");
        RANK_PRESETS.put("BUILD_TEAM", "&3[BUILD TEAM]");
    }

    public RankSpoofer() {
        super("RankSpoofer", "rankSpooferKey", "rankSpoofer", Module.Category.Render);
        INSTANCE = this;
        ClientCommandHandler.instance.registerCommand(new SetSpoofRankCommand());
        ClientCommandHandler.instance.registerCommand(new ResetSpoofRankCommand()); // Added Reset Command
        try { this.tooltip("Replaces your rank prefix. Use /setspoofrank or /resetspoofrank."); } catch (Throwable ignored) {}
    }

    @Override
    public void onEnable() {
        super.onEnable();
        MinecraftForge.EVENT_BUS.register(this);
        updateConfigValues(); 
    }

    @Override
    public void onDisable() {
        super.onDisable();
        MinecraftForge.EVENT_BUS.unregister(this);
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onChatReceived(ClientChatReceivedEvent event) {
        if (!this.getState()) return;
        if (event.type == 2) return; // Ignore Action Bar

        Minecraft mc = Minecraft.getMinecraft();
        if (mc.thePlayer == null) return;

        String myRealName = mc.thePlayer.getName();
        String originalMsg = event.message.getFormattedText();
        String unformattedMsg = event.message.getUnformattedText();

        String targetName = null;
        
        if (unformattedMsg.contains(myRealName)) {
            targetName = myRealName;
        } 

        else {
            updateConfigValues();
            if (currentSpoofName != null && !currentSpoofName.isEmpty() && unformattedMsg.contains(currentSpoofName)) {
                targetName = currentSpoofName;
            }
        }

        if (targetName == null) return;

        int cleanIndex = unformattedMsg.indexOf(targetName);
        int formattedIndex = getFormattedIndex(originalMsg, cleanIndex);
        
        if (formattedIndex > -1) {
            String newRank = currentSpoofRank.replace("&", "\u00a7") + " ";
            
            String restOfMessage = originalMsg.substring(formattedIndex);

            String newMessageText = newRank + "\u00a7r" + restOfMessage;
            
            event.message = new ChatComponentText(newMessageText);
        }
    }
    
    private int getFormattedIndex(String formatted, int unformattedIndex) {
        int cleanIdx = 0;
        for (int i = 0; i < formatted.length(); i++) {
            if (cleanIdx == unformattedIndex) return i;
            
            char c = formatted.charAt(i);
            if (c == '\u00a7' && i + 1 < formatted.length()) {
                i++; // Skip color code
            } else {
                cleanIdx++;
            }
        }
        return -1;
    }
    
    private void updateConfigValues() {
        try {
            Class<?> cfgClass = Class.forName("wtf.tatp.meowtils.config.cfg");
            Field vField = cfgClass.getDeclaredField("v");
            vField.setAccessible(true);
            Object cfgInstance = vField.get(null);
            
            if (cfgInstance != null) {
                Field rankField = cfgClass.getDeclaredField("spoofedRank");
                rankField.setAccessible(true);
                Object rankVal = rankField.get(cfgInstance);
                if (rankVal != null) currentSpoofRank = (String) rankVal;
                
                Field nameStringField = cfgClass.getDeclaredField("spoofedName");
                nameStringField.setAccessible(true);
                Object nameVal = nameStringField.get(cfgInstance);
                if (nameVal != null) currentSpoofName = (String) nameVal;
            }
        } catch (Exception e) {}
    }
    
    public void setAndSaveSpoofRank(String newRank) {
        try {
            Class<?> cfgClass = Class.forName("wtf.tatp.meowtils.config.cfg");
            Field vField = cfgClass.getDeclaredField("v");
            vField.setAccessible(true);
            Object cfgInstance = vField.get(null);
            if (cfgInstance != null) {
                Field rankField = cfgClass.getDeclaredField("spoofedRank");
                rankField.setAccessible(true);
                rankField.set(cfgInstance, newRank);
                this.currentSpoofRank = newRank;
                try {
                    Method saveMethod = cfgClass.getDeclaredMethod("save");
                    saveMethod.setAccessible(true);
                    saveMethod.invoke(null);
                } catch (Exception ex) {}
            }
        } catch (Exception e) {
            Minecraft.getMinecraft().thePlayer.addChatMessage(new ChatComponentText(EnumChatFormatting.RED + "[RankSpoofer] Failed to save config."));
        }
    }

    private static class SetSpoofRankCommand extends CommandBase {
        @Override public String getCommandName() { return "setspoofrank"; }
        @Override public String getCommandUsage(ICommandSender sender) { return "/setspoofrank <rank_name>"; }
        @Override public int getRequiredPermissionLevel() { return 0; }
        @Override public boolean canCommandSenderUseCommand(ICommandSender sender) { return true; }
        
        @Override
        public List<String> addTabCompletionOptions(ICommandSender sender, String[] args, BlockPos pos) {
            if (args.length == 1) {
                return getListOfStringsMatchingLastWord(args, RANK_PRESETS.keySet().toArray(new String[0]));
            }
            return null;
        }

        @Override
        public void processCommand(ICommandSender sender, String[] args) {
            if (INSTANCE == null) return;
            
            if (!INSTANCE.getState()) {
                 sender.addChatMessage(new ChatComponentText(EnumChatFormatting.RED + "WARNING: RankSpoofer is disabled in GUI. Enable it to see changes!"));
            }

            if (args.length == 0) {
                sender.addChatMessage(new ChatComponentText(EnumChatFormatting.GOLD + "--- Select a Rank (Click to Apply) ---"));
                for (Map.Entry<String, String> entry : RANK_PRESETS.entrySet()) {
                    String name = entry.getKey();
                    String code = entry.getValue();
                    ChatComponentText clickable = new ChatComponentText(EnumChatFormatting.GRAY + " - " + code.replace("&", "\u00a7") + " " + name);
                    clickable.getChatStyle().setChatClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/setspoofrank " + name));
                    sender.addChatMessage(clickable);
                }
                sender.addChatMessage(new ChatComponentText(EnumChatFormatting.YELLOW + "Or type a custom code: /setspoofrank &d[Custom]"));
                return;
            }

            String input = String.join(" ", args);
            String newRank;
            if (RANK_PRESETS.containsKey(input)) { newRank = RANK_PRESETS.get(input); } else { newRank = input; }
            
            INSTANCE.setAndSaveSpoofRank(newRank);
            
            String coloredRank = newRank.replace("&", "\u00a7");
            sender.addChatMessage(new ChatComponentText(EnumChatFormatting.GREEN + "[RankSpoofer] Rank set to: " + coloredRank));
        }
    }

    private static class ResetSpoofRankCommand extends CommandBase {
        @Override public String getCommandName() { return "resetspoofrank"; }
        @Override public String getCommandUsage(ICommandSender sender) { return "/resetspoofrank"; }
        @Override public int getRequiredPermissionLevel() { return 0; }
        @Override public boolean canCommandSenderUseCommand(ICommandSender sender) { return true; }

        @Override
        public void processCommand(ICommandSender sender, String[] args) {
            if (INSTANCE == null) return;
            
            String defaultRank = "&c[ADMIN]";
            INSTANCE.setAndSaveSpoofRank(defaultRank);
            
            sender.addChatMessage(new ChatComponentText(EnumChatFormatting.GREEN + "[RankSpoofer] Rank reset to default: " + defaultRank));
        }
    }
}