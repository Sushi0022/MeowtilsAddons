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
import net.minecraftforge.fml.common.gameevent.TickEvent;
import wtf.tatp.meowtils.gui.Module;
import wtf.tatp.meowtils.gui.values.ArrayValue;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class RankSpoofer extends Module {

    private static RankSpoofer INSTANCE;
    private final ArrayValue rankSelector;
    private final ArrayValue plusColorSelector;
    
    private String currentSpoofRank = "&c[ADMIN]"; 
    private String currentSpoofName = ""; 

    // Ranks that should have RED username and colon
    private static final List<String> RED_NAME_RANKS = Arrays.asList(
        "ADMIN", "OWNER", "HYPIXEL", "MCP", "MOJANG", "SLOTH", "EVENTS", "ANGUS", "APPLE", "MCProHosting"
    );

    // --- PLUS COLORS MAP ---
    private static final Map<String, String> PLUS_COLORS = new TreeMap<>();
    static {
        PLUS_COLORS.put("Red", "&c");       
        PLUS_COLORS.put("Gold", "&6");      
        PLUS_COLORS.put("Green", "&a");     
        PLUS_COLORS.put("Yellow", "&e");    
        PLUS_COLORS.put("Light Purple", "&d"); 
        PLUS_COLORS.put("White", "&f");     
        PLUS_COLORS.put("Blue", "&9");      
        PLUS_COLORS.put("Dark Green", "&2"); 
        PLUS_COLORS.put("Dark Red", "&4");   
        PLUS_COLORS.put("Dark Aqua", "&3");  
        PLUS_COLORS.put("Dark Purple", "&5"); 
        PLUS_COLORS.put("Gray", "&7");       
        PLUS_COLORS.put("Black", "&0");      
    }

    // --- NAME COLOR MAPPING ---
    private static final Map<String, String> RANK_COLORS = new HashMap<>();
    static {
        RANK_COLORS.put("MVP++", "&6");
        RANK_COLORS.put("MOJANG", "&6");
        RANK_COLORS.put("EVENTS", "&6");
        RANK_COLORS.put("APPLE", "&6");
        RANK_COLORS.put("GOD", "&6");
        RANK_COLORS.put("MVP+", "&b");
        RANK_COLORS.put("MVP", "&b");
        RANK_COLORS.put("BEAM", "&b");
        RANK_COLORS.put("Mixer", "&b");
        RANK_COLORS.put("VIP+", "&a");
        RANK_COLORS.put("VIP", "&a");
        RANK_COLORS.put("LOL", "&a");
        RANK_COLORS.put("LOL+", "&a");
        RANK_COLORS.put("JER", "&a");
        RANK_COLORS.put("JER+", "&a");
        RANK_COLORS.put("MOD", "&2");
        RANK_COLORS.put("GM", "&2");
        RANK_COLORS.put("JERRY", "&2");
        RANK_COLORS.put("JERRY+", "&2");
        RANK_COLORS.put("HELPER", "&9");
        RANK_COLORS.put("JR_HELPER", "&9");
        RANK_COLORS.put("CRINGE", "&9");
        RANK_COLORS.put("BUILD_TEAM", "&3");
        RANK_COLORS.put("BUILD_TEAM+", "&3");
        RANK_COLORS.put("WAT", "&3");
        RANK_COLORS.put("WAT+", "&3");
        RANK_COLORS.put("ADMIN", "&c");
        RANK_COLORS.put("OWNER", "&c");
        RANK_COLORS.put("YOUTUBE", "&c");
        RANK_COLORS.put("HYPIXEL", "&c");
        RANK_COLORS.put("MCP", "&c");
        RANK_COLORS.put("SLOTH", "&c");
        RANK_COLORS.put("ANGUS", "&c");
        RANK_COLORS.put("MCProHosting", "&c");
        RANK_COLORS.put("ADIM", "&c");
        RANK_COLORS.put("ADMON", "&c");
        RANK_COLORS.put("SR_JERRY", "&c");
        RANK_COLORS.put("YERRY", "&c");
        RANK_COLORS.put("MINISTER", "&c");
        RANK_COLORS.put("SALMON", "&c");
        RANK_COLORS.put("ABOVE_THE_RULES", "&c");
        RANK_COLORS.put("INNIT", "&c");
        RANK_COLORS.put("PIG", "&d");
        RANK_COLORS.put("PIG+", "&d");
        RANK_COLORS.put("PIG++", "&d");
        RANK_COLORS.put("PIG+++", "&d");
        RANK_COLORS.put("BETA_TESTER", "&d");
        RANK_COLORS.put("MAYOR", "&d");
        RANK_COLORS.put("SPECIAL", "&5");
        RANK_COLORS.put("Default", "&7");
        RANK_COLORS.put("NPC", "&8");
        RANK_COLORS.put("Custom", "&f");
    }

    // --- GUI LIST ---
    private static final Map<String, String> GUI_RANKS = new HashMap<>();
    static {
        GUI_RANKS.put("Custom", "CUSTOM");
        GUI_RANKS.put("Default", "&7");
        GUI_RANKS.put("VIP", "&a[VIP]");
        GUI_RANKS.put("VIP+", "&a[VIP&6+&a]");
        GUI_RANKS.put("MVP", "&b[MVP]");
        GUI_RANKS.put("MVP+", "&b[MVP&c+&b]");
        GUI_RANKS.put("MVP++", "&6[MVP&c++&6]");
        GUI_RANKS.put("HELPER", "&9[HELPER]");
        GUI_RANKS.put("MOD", "&2[MOD]");
        GUI_RANKS.put("GM", "&2[GM]");
        GUI_RANKS.put("ADMIN", "&c[ADMIN]");
        GUI_RANKS.put("OWNER", "&c[OWNER]");
        GUI_RANKS.put("YOUTUBE", "&c[&fYOUTUBE&c]");
        GUI_RANKS.put("HYPIXEL", "&c[&6ዞ&c]"); // Updated with unicode ዞ
        GUI_RANKS.put("SLOTH", "&c[SLOTH]");
        GUI_RANKS.put("MOJANG", "&6[MOJANG]");
    }

    // --- ALL RANKS ---
    private static final Map<String, String> ALL_RANKS = new HashMap<>(GUI_RANKS);
    static {
        ALL_RANKS.put("EVENTS", "&6[EVENTS]");
        ALL_RANKS.put("MCP", "&c[MCP]");
        ALL_RANKS.put("INNIT", "&c[INNIT]");
        ALL_RANKS.put("PIG", "&d[PIG]");
        ALL_RANKS.put("PIG+", "&d[PIG&b+&d]");
        ALL_RANKS.put("PIG++", "&d[PIG&b++&d]");
        ALL_RANKS.put("PIG+++", "&d[PIG&b+++&d]");
        ALL_RANKS.put("NPC", "&8[NPC]");
        ALL_RANKS.put("BUILD_TEAM", "&3[BUILD TEAM]");
        ALL_RANKS.put("CRINGE", "&9[CRINGE]"); 
        ALL_RANKS.put("SALMON", "&c[SALMON]"); 
        ALL_RANKS.put("JERRY", "&2[JERRY]");
        ALL_RANKS.put("MAYOR", "&d[MAYOR]");
        ALL_RANKS.put("GM", "&2[GM]");
    }

    public RankSpoofer() {
        super("RankSpoofer", "rankSpooferKey", "rankSpoofer", Module.Category.Render);
        INSTANCE = this;
        
        List<String> options = new ArrayList<>(GUI_RANKS.keySet());
        this.rankSelector = new ArrayValue("Rank", options, "rankSpooferSelection");
        this.addArray(this.rankSelector);

        List<String> colorOptions = new ArrayList<>(PLUS_COLORS.keySet());
        this.plusColorSelector = new ArrayValue("MVP+ Color", colorOptions, "rankSpooferPlusColor");
        this.addArray(this.plusColorSelector);
        
        ClientCommandHandler.instance.registerCommand(new SetSpoofRankCommand());
        ClientCommandHandler.instance.registerCommand(new ResetSpoofRankCommand());
        
        try { this.tooltip("Replaces rank prefix. Customize MVP+ color in settings."); } catch (Throwable ignored) {}
    }

    @Override
    public void onEnable() {
        super.onEnable();
        MinecraftForge.EVENT_BUS.register(this);
        updateConfigValues(); 
        updateVisibility(); 
    }

    @Override
    public void onDisable() {
        super.onDisable();
        MinecraftForge.EVENT_BUS.unregister(this);
    }

    // --- VISIBILITY TOGGLER ---
    @SubscribeEvent
    public void onTick(TickEvent.ClientTickEvent event) {
        if (event.phase == TickEvent.Phase.START && this.getState()) {
            updateVisibility();
        }
    }

    private void updateVisibility() {
        try {
            String selectedRank = this.rankSelector.getValue();
            boolean showPlusOptions = selectedRank.equals("MVP+") || selectedRank.equals("MVP++");

            Class<?> valueClass = this.plusColorSelector.getClass().getSuperclass(); 
            try {
                Field visibleField = valueClass.getDeclaredField("visible");
                visibleField.setAccessible(true);
                visibleField.setBoolean(this.plusColorSelector, showPlusOptions);
            } catch (NoSuchFieldException e) {}
        } catch (Exception e) {}
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onChatReceived(ClientChatReceivedEvent event) {
        if (!this.getState() || event.type == 2) return; 

        Minecraft mc = Minecraft.getMinecraft();
        if (mc.thePlayer == null) return;

        String myRealName = mc.thePlayer.getName();
        String originalMsg = event.message.getFormattedText();
        String unformattedMsg = event.message.getUnformattedText();

        // 1. SYNC WITH DROPDOWN
        String selectedKey = this.rankSelector.getValue();
        if (!selectedKey.equals("Custom") && GUI_RANKS.containsKey(selectedKey)) {
            this.currentSpoofRank = GUI_RANKS.get(selectedKey);
            
            // --- DYNAMIC PLUS COLOR LOGIC ---
            if (selectedKey.equals("MVP+") || selectedKey.equals("MVP++")) {
                String plusColorName = this.plusColorSelector.getValue();
                String plusCode = PLUS_COLORS.get(plusColorName);
                if (plusCode == null) plusCode = "&c"; 

                if (selectedKey.equals("MVP+")) {
                    this.currentSpoofRank = "&b[MVP" + plusCode + "+&b]";
                } else {
                    this.currentSpoofRank = "&6[MVP" + plusCode + "++&6]";
                }
            }
        } 

        if (currentSpoofRank.equals("RESET")) return;

        // 2. Determine Target Name
        String targetName = null;
        updateNameSpooferConfig();
        
        if (unformattedMsg.contains(myRealName)) {
            targetName = myRealName;
        } else if (currentSpoofName != null && !currentSpoofName.isEmpty() && unformattedMsg.contains(currentSpoofName)) {
            targetName = currentSpoofName;
        }

        if (targetName == null) return; 

        // 3. Color-Safe Regex Replacement
        String safeRegex = makeColorSafeRegex(targetName);
        Pattern p = Pattern.compile("^(.*?)(" + safeRegex + ")");
        Matcher m = p.matcher(originalMsg);

        if (m.find()) {
            String matchedName = m.group(2);
            String restOfMessage = originalMsg.substring(m.end()); 
            
            String newRankPrefix = currentSpoofRank.replace("&", "\u00a7") + " ";
            
            // --- COLOR LOGIC ---
            String nameColorCode = RANK_COLORS.getOrDefault(selectedKey, "&f");
            String nameColor = nameColorCode.replace("&", "\u00a7");
            
            String colonColor = selectedKey.equals("Default") ? "\u00a77" : "\u00a7f";
            
            String messageColor = "\u00a7f"; 
            if (RED_NAME_RANKS.contains(selectedKey)) {
                messageColor = "\u00a7c"; 
            } else if (selectedKey.equals("Default")) {
                messageColor = "\u00a77";
            }

            int colonIndex = restOfMessage.indexOf(":");
            if (colonIndex > -1) {
                String preColon = restOfMessage.substring(0, colonIndex + 1); 
                String postColon = restOfMessage.substring(colonIndex + 1);
                
                int contentStart = 0;
                while (contentStart < postColon.length() && postColon.charAt(contentStart) == ' ') {
                    contentStart++;
                }
                if (contentStart + 1 < postColon.length() && postColon.charAt(contentStart) == '\u00a7') {
                    contentStart += 2;
                }
                
                restOfMessage = preColon + " " + messageColor + postColon.substring(contentStart);
                // Apply colon color
                restOfMessage = colonColor + restOfMessage.substring(2); 
            }

            String finalMsg = newRankPrefix + nameColor + matchedName + "\u00a7r" + restOfMessage;
            event.message = new ChatComponentText(finalMsg);
        }
    }
    
    private String makeColorSafeRegex(String input) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < input.length(); i++) {
            char c = input.charAt(i);
            if ("[](){}.*+?^$|\\".indexOf(c) != -1) sb.append("\\").append(c);
            else sb.append(c);
            if (i < input.length() - 1) sb.append("(?:\\u00a7.)*?");
        }
        return sb.toString();
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
    
    private void updateNameSpooferConfig() {
        try {
            Class<?> cfgClass = Class.forName("wtf.tatp.meowtils.config.cfg");
            Field vField = cfgClass.getDeclaredField("v");
            vField.setAccessible(true);
            Object cfgInstance = vField.get(null);
            
            if (cfgInstance != null) {
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
                Method saveMethod = cfgClass.getDeclaredMethod("save");
                saveMethod.setAccessible(true);
                saveMethod.invoke(null);
            }
        } catch (Exception e) {}
    }
    
    private void setDropdownToCustom() {
        try {
            Method setMethod = null;
            try { setMethod = rankSelector.getClass().getMethod("setValue", String.class); } catch(Exception e){}
            if (setMethod == null) try { setMethod = rankSelector.getClass().getMethod("set", String.class); } catch(Exception e){}
            if (setMethod != null) {
                setMethod.invoke(rankSelector, "Custom");
            }
        } catch (Exception e) {}
    }

    private static class SetSpoofRankCommand extends CommandBase {
        @Override public String getCommandName() { return "setspoofrank"; }
        @Override public String getCommandUsage(ICommandSender sender) { return "/setspoofrank <rank_name>"; }
        @Override public int getRequiredPermissionLevel() { return 0; }
        @Override public boolean canCommandSenderUseCommand(ICommandSender sender) { return true; }
        
        @Override
        public List<String> addTabCompletionOptions(ICommandSender sender, String[] args, BlockPos pos) {
            if (args.length == 1) return getListOfStringsMatchingLastWord(args, ALL_RANKS.keySet().toArray(new String[0]));
            return null;
        }

        @Override
        public void processCommand(ICommandSender sender, String[] args) {
            if (INSTANCE == null) return;
            if (args.length == 0) {
                sender.addChatMessage(new ChatComponentText(EnumChatFormatting.RED + "Usage: /setspoofrank <RankName>"));
                return;
            }

            String input = String.join(" ", args);
            String newRank;
            if (ALL_RANKS.containsKey(input)) { newRank = ALL_RANKS.get(input); } else { newRank = input; }
            
            INSTANCE.setAndSaveSpoofRank(newRank);
            INSTANCE.setDropdownToCustom();
            
            String coloredRank = newRank.replace("&", "\u00a7");
            sender.addChatMessage(new ChatComponentText(EnumChatFormatting.GREEN + "[RankSpoofer] Rank set to: " + coloredRank));
        }
    }

    private static class ResetSpoofRankCommand extends CommandBase {
        @Override public String getCommandName() { return "resetspoofrank"; }
        @Override public String getCommandUsage(ICommandSender sender) { return "/resetspoofrank"; }
        @Override public int getRequiredPermissionLevel() { return 0; }
        @Override public boolean canCommandSenderUseCommand(ICommandSender sender) { return true; }
        @Override public void processCommand(ICommandSender sender, String[] args) {
            if (INSTANCE == null) return;
            INSTANCE.setAndSaveSpoofRank("RESET");
            sender.addChatMessage(new ChatComponentText(EnumChatFormatting.GREEN + "[RankSpoofer] Rank reset to original server rank."));
        }
    }
}