package com.github.tewxx.meowtilsaddons.modules;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen; // Needed for the move screen
import net.minecraft.client.gui.inventory.GuiChest;
import net.minecraft.command.CommandBase;
import net.minecraft.command.ICommandSender;
import net.minecraft.inventory.ContainerChest;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.network.play.server.S30PacketWindowItems; 
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.ChatComponentText;
import net.minecraftforge.client.ClientCommandHandler;
import net.minecraftforge.client.event.ClientChatReceivedEvent;
import net.minecraftforge.client.event.RenderGameOverlayEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import wtf.tatp.meowtils.gui.Module;

import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class AutomatedQuestTracker extends Module {

    private static AutomatedQuestTracker INSTANCE;
    
    // --- CONFIG VARIABLES (Default) ---
    // We store these locally to avoid constant reflection
    public static int hudX = 5;
    public static int hudY = 150;
    
    private List<String> apiLines = new ArrayList<>();
    private long lastFetchTime = 0;
    private static final long FETCH_COOLDOWN = 60000; 

    private final Map<String, Quest> activeQuests = new ConcurrentHashMap<>();
    
    private static final Pattern START_PATTERN = Pattern.compile("You started the (.*): (.*) quest!");
    private static final Pattern COMPLETE_PATTERN = Pattern.compile(".* Quest: .* completed!");
    private static final String COMPLETE_MESSAGE_SNIPPET = "Quest complete"; 
    private static final Pattern PROGRESS_PATTERN = Pattern.compile("(?:Progress:\\s*|\\()(\\d+)\\/(\\-?\\d+)\\)?");

    private static final String[] KEYWORDS_WIN = {"Win", "Victory", "Champion"};
    private static final String[] KEYWORDS_PLAY = {"Play", "Game", "Participate", "Complete", "Survive"};
    private static final String[] KEYWORDS_KILL = {"Kill", "Slay", "Head", "Bounty", "Hunt", "Eliminate", "Tag"};
    private static final String[] KEYWORDS_BED = {"Bed", "Break"};
    private static final String[] CHAT_WINS = {"You won!", "has won the game!", "WINNER!", "1st Place", "Victory!"};
    private static final String[] CHAT_GAME_OVER = {"Game Over", "You died", "Eliminated", "Knocked out"};

    public AutomatedQuestTracker() {
        super("AutomatedQuestTracker", "trackerModuleKey", "trackerModule", Module.Category.Advanced);
        INSTANCE = this; 
        
        // Register the move command
        ClientCommandHandler.instance.registerCommand(new MoveCommand());
        
        // Load initial position from config
        loadConfigPosition();

        try { 
            this.tooltip("Tracks active quests. Use /movequest to reposition."); 
        } catch (Throwable ignored) {}
    }
    
    private static class Quest {
        public final String name;
        public int progress;
        public int goal;
        public String type; 
        public Quest(String name, int progress, int goal, String type) {
            this.name = name;
            this.progress = progress;
            this.goal = goal;
            this.type = type;
        }
    }

    public void startFullQuest(String name, int progress, int goal, String type) {
        String trimmedName = name.trim();
        if (!this.getState() || trimmedName.isEmpty() || goal <= 0 || progress < 0) return;
        if (!activeQuests.containsKey(trimmedName) || activeQuests.get(trimmedName).progress < progress) {
            activeQuests.put(trimmedName, new Quest(trimmedName, progress, goal, type));
        }
    }
    
    @Override
    public void onEnable() {
        super.onEnable();
        MinecraftForge.EVENT_BUS.register(this);
        fetchQuestData();
    }

    @Override
    public void onDisable() {
        super.onDisable();
        MinecraftForge.EVENT_BUS.unregister(this);
        activeQuests.clear();
    }

    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc == null || mc.thePlayer == null || event.phase != TickEvent.Phase.START || !this.getState()) return;
        if (System.currentTimeMillis() - lastFetchTime > FETCH_COOLDOWN && activeQuests.isEmpty()) fetchQuestData();
    }

    // --- RENDER LOGIC ---
    @SubscribeEvent
    public void onRenderOverlay(RenderGameOverlayEvent.Text event) {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc == null || mc.fontRendererObj == null) return;

        // Don't render in HUD editor if we are in our own move screen
        if (mc.currentScreen instanceof QuestPositionGui) return;

        List<String> linesToRender = getLiveQuestLines();
        if (linesToRender.isEmpty()) linesToRender = apiLines; 
        if (linesToRender.isEmpty()) return;

        int startX = hudX;
        int startY = hudY;
        
        // Simple background
        int backgroundColor = 0x90000000; 
        int padding = 2;
        int width = 0;
        int height = linesToRender.size() * 10;
        
        for (String line : linesToRender) {
            int lineWidth = mc.fontRendererObj.getStringWidth(line);
            if (lineWidth > width) width = lineWidth;
        }
        
        net.minecraft.client.gui.Gui.drawRect(startX - padding, startY - padding, startX + width + padding, startY + height + padding, backgroundColor);

        for (String line : linesToRender) {
            mc.fontRendererObj.drawStringWithShadow(line, startX, startY, 0xFFFFFF);
            startY += 10;
        }
    }
    
    // --- HELPER: Load/Save Config via Reflection ---
    public static void loadConfigPosition() {
        try {
            Class<?> cfgClass = Class.forName("wtf.tatp.meowtils.config.cfg");
            Field vField = cfgClass.getDeclaredField("v");
            vField.setAccessible(true);
            Object cfgInstance = vField.get(null);
            
            if (cfgInstance != null) {
                hudX = (int) cfgClass.getDeclaredField("questHUD_x").get(cfgInstance);
                hudY = (int) cfgClass.getDeclaredField("questHUD_y").get(cfgInstance);
            }
        } catch (Exception e) {}
    }

    public static void saveConfigPosition() {
        try {
            Class<?> cfgClass = Class.forName("wtf.tatp.meowtils.config.cfg");
            Field vField = cfgClass.getDeclaredField("v");
            vField.setAccessible(true);
            Object cfgInstance = vField.get(null);
            
            if (cfgInstance != null) {
                cfgClass.getDeclaredField("questHUD_x").setInt(cfgInstance, hudX);
                cfgClass.getDeclaredField("questHUD_y").setInt(cfgInstance, hudY);
                
                Method saveMethod = cfgClass.getDeclaredMethod("save");
                saveMethod.invoke(null);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private List<String> getLiveQuestLines() {
        List<String> lines = new ArrayList<>();
        if (activeQuests.isEmpty()) return lines;
        lines.add(EnumChatFormatting.AQUA + "-- Live Quests Progress --");
        for (Quest quest : activeQuests.values()) {
            int goal = quest.goal;
            if (goal <= 0) goal = 1; 
            String progressLine;
            if (quest.progress >= quest.goal) {
                progressLine = EnumChatFormatting.GREEN + "[COMPLETE] " + quest.name;
            } else {
                double percent = (quest.progress / (double)goal) * 100;
                String percentStr = String.format("%.0f", percent) + "%";
                progressLine = String.format("%s[%d/%d] %s (%s)", EnumChatFormatting.YELLOW, quest.progress, goal, quest.name, percentStr);
            }
            lines.add(progressLine);
        }
        return lines;
    }
    
    // --- PACKET SNIFFING & CHAT LOGIC (Same as before) ---
    public static void onPacketReceived(S30PacketWindowItems packet) {
        if (INSTANCE == null || !INSTANCE.getState()) return;
        Minecraft mc = Minecraft.getMinecraft();
        if (!(mc.currentScreen instanceof GuiChest)) return;
        GuiChest chestGui = (GuiChest) mc.currentScreen;
        if (!(chestGui.inventorySlots instanceof ContainerChest)) return;
        ContainerChest container = (ContainerChest) chestGui.inventorySlots;
        String cleanedGuiName = EnumChatFormatting.getTextWithoutFormattingCodes(container.getLowerChestInventory().getDisplayName().getUnformattedText()).trim();
        
        if (!cleanedGuiName.endsWith(" Quests") || cleanedGuiName.equals("Quests & Challenges")) return;

        Object rawItemsObject = getFieldReflection(packet, "itemStacks", "field_148910_b", "field_148913_b");
        if (rawItemsObject == null) rawItemsObject = callMethodReflection(packet, "getItemStacks", "func_149112_c");
        if (rawItemsObject == null) return;

        ItemStack[] itemStacks = null;
        if (rawItemsObject instanceof ItemStack[]) {
            itemStacks = (ItemStack[]) rawItemsObject;
        } else if (rawItemsObject instanceof List) {
            List<?> list = (List<?>) rawItemsObject;
            itemStacks = list.toArray(new ItemStack[0]);
        }

        if (itemStacks == null || itemStacks.length < 45) return;

        boolean hasQuestItem = false;
        for (ItemStack stack : itemStacks) {
            if (stack != null && stack.getUnlocalizedName().contains("paper")) {
                String name = EnumChatFormatting.getTextWithoutFormattingCodes(stack.getDisplayName());
                if (name.contains("Daily Quest") || name.contains("Weekly Quest") || name.contains("Quest:")) {
                    hasQuestItem = true; break;
                }
            }
        }
        if (!hasQuestItem) return;

        INSTANCE.activeQuests.clear();
        int parsedCount = 0;

        for (int i = 0; i < itemStacks.length; i++) {
            ItemStack stack = itemStacks[i];
            if (stack == null) continue;
            
            String rawName = stack.getDisplayName();
            String name = EnumChatFormatting.getTextWithoutFormattingCodes(rawName).trim();
            
            String questName = "";
            int progress = -1;
            int goal = -1; 
            String type = "OTHER";
            
            if (stack.hasTagCompound() && stack.getTagCompound().hasKey("display", 10)) {
                if (name.contains(":")) {
                    String[] parts = name.split(":", 2);
                    if (parts.length == 2) questName = parts[1].trim();
                } else {
                    questName = name;
                }

                NBTTagCompound displayTag = stack.getTagCompound().getCompoundTag("display");
                if (displayTag.hasKey("Lore", 9)) {
                    NBTTagList loreList = displayTag.getTagList("Lore", 8);
                    for (int j = 0; j < loreList.tagCount(); j++) {
                        String loreLine = EnumChatFormatting.getTextWithoutFormattingCodes(loreList.getStringTagAt(j));
                        if (loreLine.contains("Click to start")) break; 

                        Matcher progressMatcher = PROGRESS_PATTERN.matcher(loreLine);
                        if (progressMatcher.find() && progressMatcher.groupCount() >= 2) {
                            try {
                                progress = Integer.parseInt(progressMatcher.group(1));
                                goal = Integer.parseInt(progressMatcher.group(2));
                                
                                if (containsAny(loreLine, KEYWORDS_WIN) || containsAny(name, KEYWORDS_WIN)) type = "WIN";
                                else if (containsAny(loreLine, KEYWORDS_KILL) || containsAny(name, KEYWORDS_KILL)) type = "KILL";
                                else if (containsAny(loreLine, KEYWORDS_BED) || containsAny(name, KEYWORDS_BED)) type = "BED";
                                else if (containsAny(loreLine, KEYWORDS_PLAY) || containsAny(name, KEYWORDS_PLAY)) type = "PLAY";
                                break;
                            } catch (NumberFormatException ignored) { }
                        }
                    }
                }
            }
            
            if (questName.isEmpty()) questName = name;

            if (!questName.isEmpty() && goal > 0 && progress > -1) {
                INSTANCE.startFullQuest(questName, progress, goal, type);
                parsedCount++;
            }
        } 

        if (parsedCount > 0) {
             String gameName = cleanedGuiName.replace(" Quests", "");
             mc.thePlayer.addChatMessage(new ChatComponentText(EnumChatFormatting.AQUA + "[QuestHUD] " + EnumChatFormatting.GREEN + "Synced " + parsedCount + " " + gameName + " quests."));
        }
    }
    
    @SubscribeEvent
    public void onChatReceived(ClientChatReceivedEvent event) {
        if (!this.getState()) return;
        String message = EnumChatFormatting.getTextWithoutFormattingCodes(event.message.getUnformattedText());
        Minecraft mc = Minecraft.getMinecraft();
        
        Matcher startMatcher = START_PATTERN.matcher(message);
        if (startMatcher.find()) {
            String questName = startMatcher.group(2).trim();
            if (!activeQuests.containsKey(questName)) activeQuests.put(questName, new Quest(questName, 0, 1, "OTHER"));
        }

        if (!activeQuests.isEmpty() && mc != null && mc.thePlayer != null) {
            String playerName = mc.thePlayer.getName();
            
            boolean isWin = false;
            for (String winMsg : CHAT_WINS) { if (message.contains(winMsg)) { isWin = true; break; } }
            boolean isGameOver = isWin;
            if (!isGameOver) { for (String overMsg : CHAT_GAME_OVER) { if (message.contains(overMsg)) { isGameOver = true; break; } } }
            boolean isKill = false;
            if (message.contains(playerName) && (message.contains("by " + playerName) || message.contains("slain by " + playerName) || message.contains("tagged"))) isKill = true;

            for (Quest quest : activeQuests.values()) {
                boolean increment = false;
                String type = quest.type;
                if (isWin && type.equals("WIN")) increment = true;
                if ((isGameOver || isWin) && type.equals("PLAY")) increment = true;
                if (isKill && type.equals("KILL")) {
                     boolean isFinalKill = message.contains("FINAL KILL");
                     boolean questRequiresFinal = quest.name.contains("Final");
                     if (questRequiresFinal) { if (isFinalKill) increment = true; } else { increment = true; }
                }
                if (type.equals("BED") && message.contains("BED DESTRUCTION") && message.contains("by " + playerName)) increment = true;
                if (increment) quest.progress++;
            }
        }
        
        if (COMPLETE_PATTERN.matcher(message).find() || message.contains(COMPLETE_MESSAGE_SNIPPET)) {
            String completionRegex = ".* Quest: (.*) completed!";
            Pattern pattern = Pattern.compile(completionRegex);
            Matcher matcher = pattern.matcher(message);
            String completedQuestName = null;
            if (matcher.find()) completedQuestName = matcher.group(1).trim();
            if (completedQuestName != null && activeQuests.containsKey(completedQuestName)) {
                activeQuests.remove(completedQuestName);
            } else if (activeQuests.size() == 1) {
                activeQuests.clear(); 
            }
        }
    }
    
    private static Object getFieldReflection(Object obj, String... names) {
        for (String name : names) { try { Field f = obj.getClass().getDeclaredField(name); f.setAccessible(true); return f.get(obj); } catch (Exception e) {} } return null;
    }
    private static Object callMethodReflection(Object obj, String... names) {
        for (String name : names) { try { Method m = obj.getClass().getMethod(name); m.setAccessible(true); return m.invoke(obj); } catch (Exception e) {} } return null;
    }
    private static boolean containsAny(String input, String[] keywords) {
        for (String k : keywords) if (input.contains(k)) return true; return false;
    }
    
    private void fetchQuestData() { /* No-Op: Manual API removed */ }
    
    
    // --- CUSTOM MOVE SCREEN ---
    public static class QuestPositionGui extends GuiScreen {
        private boolean dragging = false;
        private int dragOffsetX, dragOffsetY;

        @Override
        public void drawScreen(int mouseX, int mouseY, float partialTicks) {
            this.drawDefaultBackground();
            
            String sample = "Quest: Example Quest (0/1)";
            int width = fontRendererObj.getStringWidth(sample) + 4;
            int height = 12;
            
            // Draw box at current pos
            drawRect(AutomatedQuestTracker.hudX - 2, AutomatedQuestTracker.hudY - 2, 
                     AutomatedQuestTracker.hudX + width + 2, AutomatedQuestTracker.hudY + height + 2, 0x80FFFFFF);
                     
            fontRendererObj.drawStringWithShadow(sample, AutomatedQuestTracker.hudX, AutomatedQuestTracker.hudY, 0xFFFFFF);
            drawCenteredString(fontRendererObj, "Click and drag to move. Press ESC to save.", this.width / 2, 20, 0xFFFFFF);
            
            if (dragging) {
                AutomatedQuestTracker.hudX = mouseX - dragOffsetX;
                AutomatedQuestTracker.hudY = mouseY - dragOffsetY;
            }
            
            super.drawScreen(mouseX, mouseY, partialTicks);
        }

        @Override
        protected void mouseClicked(int mouseX, int mouseY, int mouseButton) throws IOException {
            super.mouseClicked(mouseX, mouseY, mouseButton);
            if (mouseButton == 0) {
                // Check collision
                String sample = "Quest: Example Quest (0/1)";
                int width = fontRendererObj.getStringWidth(sample) + 4;
                int height = 12;
                
                if (mouseX >= AutomatedQuestTracker.hudX && mouseX <= AutomatedQuestTracker.hudX + width &&
                    mouseY >= AutomatedQuestTracker.hudY && mouseY <= AutomatedQuestTracker.hudY + height) {
                    dragging = true;
                    dragOffsetX = mouseX - AutomatedQuestTracker.hudX;
                    dragOffsetY = mouseY - AutomatedQuestTracker.hudY;
                }
            }
        }

        @Override
        protected void mouseReleased(int mouseX, int mouseY, int state) {
            super.mouseReleased(mouseX, mouseY, state);
            dragging = false;
        }

        @Override
        public void onGuiClosed() {
            AutomatedQuestTracker.saveConfigPosition();
        }
    }

    private static class MoveCommand extends CommandBase {
        @Override public String getCommandName() { return "movequest"; }
        @Override public String getCommandUsage(ICommandSender sender) { return "/movequest"; }
        @Override public int getRequiredPermissionLevel() { return 0; }
        @Override public boolean canCommandSenderUseCommand(ICommandSender sender) { return true; }
        @Override public void processCommand(ICommandSender sender, String[] args) {
            // Delay opening screen by 1 tick to avoid closing chat immediately
            new Thread(() -> {
                try { Thread.sleep(100); } catch (Exception e) {}
                Minecraft.getMinecraft().addScheduledTask(() -> 
                    Minecraft.getMinecraft().displayGuiScreen(new QuestPositionGui())
                );
            }).start();
        }
    }
}