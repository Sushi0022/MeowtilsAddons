package com.github.tewxx.meowtilsaddons.modules;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.client.Minecraft;
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

import java.io.InputStreamReader;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class AutomatedQuestTracker extends Module {

    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private static AutomatedQuestTracker INSTANCE;
    
    private List<String> apiLines = new ArrayList<>();
    private long lastFetchTime = 0;
    private static final long FETCH_COOLDOWN = 60000; 

    private final Map<String, Quest> activeQuests = new ConcurrentHashMap<>();
    public static boolean debugMode = false; 

    // --- PATTERNS ---
    private static final Pattern START_PATTERN = Pattern.compile("You started the (.*): (.*) quest!");
    private static final Pattern COMPLETE_PATTERN = Pattern.compile(".* Quest: .* completed!");
    private static final String COMPLETE_MESSAGE_SNIPPET = "Quest complete"; 
    // Regex matches "(1/5)" and "Progress: 1/5"
    private static final Pattern PROGRESS_PATTERN = Pattern.compile("(?:Progress:\\s*|\\()(\\d+)\\/(\\-?\\d+)\\)?");

    // --- KEYWORDS ---
    private static final String[] KEYWORDS_WIN = {"Win", "Victory", "Champion"};
    private static final String[] KEYWORDS_PLAY = {"Play", "Game", "Participate", "Complete", "Survive"};
    private static final String[] KEYWORDS_KILL = {"Kill", "Slay", "Head", "Bounty", "Hunt", "Eliminate", "Tag"};
    private static final String[] KEYWORDS_BED = {"Bed", "Break"};
    
    private static final String[] CHAT_WINS = {"You won!", "has won the game!", "WINNER!", "1st Place", "Victory!"};
    private static final String[] CHAT_GAME_OVER = {"Game Over", "You died", "Eliminated", "Knocked out"};

    public AutomatedQuestTracker() {
        super("AutomatedQuestTracker", "trackerModuleKey", "trackerModule", Module.Category.Advanced);
        INSTANCE = this; 
        
        ClientCommandHandler.instance.registerCommand(new DebugCommand());

        try { 
            this.tooltip("Universal Quest Tracker. Syncs via GUI, updates via Chat."); 
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
        apiLines.clear();
        activeQuests.clear();
        debugMode = false;
    }

    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc == null || mc.thePlayer == null || event.phase != TickEvent.Phase.START || !this.getState()) return;
        if (System.currentTimeMillis() - lastFetchTime > FETCH_COOLDOWN && activeQuests.isEmpty()) fetchQuestData();
    }

    // --- PACKET SNIFFING LOGIC (Universal) ---
    public static void onPacketReceived(S30PacketWindowItems packet) {
        if (INSTANCE == null || !INSTANCE.getState()) return;
        Minecraft mc = Minecraft.getMinecraft();
        
        if (!(mc.currentScreen instanceof GuiChest)) return;
        GuiChest chestGui = (GuiChest) mc.currentScreen;
        if (!(chestGui.inventorySlots instanceof ContainerChest)) return;

        ContainerChest container = (ContainerChest) chestGui.inventorySlots;
        String cleanedGuiName = EnumChatFormatting.getTextWithoutFormattingCodes(
            container.getLowerChestInventory().getDisplayName().getUnformattedText()
        ).trim();

        if (!cleanedGuiName.endsWith(" Quests") || cleanedGuiName.equals("Quests & Challenges")) return;

        if (debugMode) mc.thePlayer.addChatMessage(new ChatComponentText(EnumChatFormatting.DARK_AQUA + "[QuestDebug] Found GUI: " + cleanedGuiName));

        Object rawItemsObject = getFieldReflection(packet, "itemStacks", "field_148910_b", "field_148913_b");
        if (rawItemsObject == null) {
            rawItemsObject = callMethodReflection(packet, "getItemStacks", "func_149112_c");
        }
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
            if (stack != null) {
                String name = EnumChatFormatting.getTextWithoutFormattingCodes(stack.getDisplayName());
                // Relaxed check for any Quest-like item
                if (name.contains("Daily Quest") || name.contains("Weekly Quest") || name.contains("Quest:")) {
                    hasQuestItem = true;
                    break;
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
            
            // Universal check: must be a Quest item
            if (!name.contains("Daily Quest:") && !name.contains("Weekly Quest:")) continue;

            String questName = "";
            int progress = -1;
            int goal = -1; 
            String type = "OTHER";
            boolean isInactive = false;
            
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
                        
                        if (loreLine.contains("Click to start")) {
                            isInactive = true;
                            break;
                        }

                        Matcher progressMatcher = PROGRESS_PATTERN.matcher(loreLine);
                        if (progressMatcher.find() && progressMatcher.groupCount() >= 2) {
                            try {
                                progress = Integer.parseInt(progressMatcher.group(1));
                                goal = Integer.parseInt(progressMatcher.group(2));
                                
                                // Keyword Association
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

            if (isInactive) continue;

            if (!questName.isEmpty() && goal > 0 && progress > -1) {
                INSTANCE.startFullQuest(questName, progress, goal, type);
                parsedCount++;
            }
        } 

        if (parsedCount > 0) {
             String gameName = cleanedGuiName.replace(" Quests", "");
             mc.thePlayer.addChatMessage(new ChatComponentText(EnumChatFormatting.AQUA + 
                "[QuestHUD] " + EnumChatFormatting.GREEN + "Synced " + parsedCount + " " + gameName + " quests."
            ));
        }
    }
    
    private static Object getFieldReflection(Object obj, String... names) {
        for (String name : names) {
            try {
                Field field = obj.getClass().getDeclaredField(name);
                field.setAccessible(true);
                return field.get(obj);
            } catch (Exception ignored) {}
        }
        return null;
    }
    
    private static Object callMethodReflection(Object obj, String... names) {
        for (String name : names) {
            try {
                Method method = obj.getClass().getMethod(name);
                method.setAccessible(true);
                return method.invoke(obj);
            } catch (Exception ignored) {}
        }
        return null;
    }

    // --- UNIVERSAL CHAT LOGIC ---
    @SubscribeEvent
    public void onChatReceived(ClientChatReceivedEvent event) {
        if (!this.getState()) return;
        
        String message = EnumChatFormatting.getTextWithoutFormattingCodes(event.message.getUnformattedText());
        Minecraft mc = Minecraft.getMinecraft();
        
        // 1. Auto-Start Quest (MVP+)
        Matcher startMatcher = START_PATTERN.matcher(message);
        if (startMatcher.find()) {
            String questName = startMatcher.group(2).trim();
            if (!activeQuests.containsKey(questName)) {
                String type = "OTHER";
                if (containsAny(questName, KEYWORDS_WIN)) type = "WIN";
                else if (containsAny(questName, KEYWORDS_KILL)) type = "KILL";
                else if (containsAny(questName, KEYWORDS_BED)) type = "BED";
                else if (containsAny(questName, KEYWORDS_PLAY)) type = "PLAY";
                activeQuests.put(questName, new Quest(questName, 0, 1, type));
            }
        }

        if (!activeQuests.isEmpty() && mc != null && mc.thePlayer != null) {
            String playerName = mc.thePlayer.getName();
            
            boolean isWin = false;
            for (String winMsg : CHAT_WINS) { if (message.contains(winMsg)) { isWin = true; break; } }
            
            boolean isGameOver = isWin;
            if (!isGameOver) {
                for (String overMsg : CHAT_GAME_OVER) { if (message.contains(overMsg)) { isGameOver = true; break; } }
            }

            boolean isKill = false;
            // Catch "killed by <You>", "slain by <You>", "tagged <You>"
            // Refined to ensure YOU are the subject doing the tagging in TNT Tag
            if (message.contains(playerName)) {
                 if (message.contains("by " + playerName)) {
                     isKill = true;
                 } else if (message.startsWith(playerName + " tagged")) {
                     // Handle Active "You tagged someone" (TNT Tag)
                     isKill = true;
                 }
            }

            for (Quest quest : activeQuests.values()) {
                boolean increment = false;
                String type = quest.type;

                // WIN
                if (isWin) {
                    if (type.equals("WIN")) increment = true;
                }

                // PLAY/PARTICIPATE
                if ((isGameOver || isWin) && type.equals("PLAY")) increment = true;

                // KILLS
                if (isKill && type.equals("KILL")) {
                     boolean isFinalKill = message.contains("FINAL KILL");
                     boolean questRequiresFinal = quest.name.contains("Final");
                     
                     if (questRequiresFinal) {
                         if (isFinalKill) increment = true;
                     } else {
                         increment = true;
                     }
                }
                
                // BEDWARS SPECIFIC
                if (type.equals("BED") && message.contains("BED DESTRUCTION") && message.contains("by " + playerName)) {
                    increment = true;
                }

                if (increment) {
                    quest.progress++;
                    if (debugMode) mc.thePlayer.addChatMessage(new ChatComponentText(EnumChatFormatting.GREEN + "[QuestDebug] Progressed: " + quest.name));
                }
            }
        }
        
        // 3. Complete Quest
        if (COMPLETE_PATTERN.matcher(message).find() || message.contains(COMPLETE_MESSAGE_SNIPPET)) {
            String completionRegex = ".* Quest: (.*) completed!";
            Pattern pattern = Pattern.compile(completionRegex);
            Matcher matcher = pattern.matcher(message);
            String completedQuestName = null;
            if (matcher.find()) completedQuestName = matcher.group(1).trim();
            
            if (completedQuestName != null && activeQuests.containsKey(completedQuestName)) {
                final String finalName = completedQuestName;
                executor.submit(() -> {
                    try { Thread.sleep(5000); } catch (InterruptedException ignored) {}
                    activeQuests.remove(finalName);
                    lastFetchTime = 0; 
                });
            } else if (activeQuests.size() == 1) {
                 executor.submit(() -> {
                    try { Thread.sleep(5000); } catch (InterruptedException ignored) {}
                    activeQuests.clear(); 
                    lastFetchTime = 0;
                });
            }
        }
    }

    private static boolean containsAny(String input, String[] keywords) {
        for (String keyword : keywords) {
            if (input.contains(keyword)) return true;
        }
        return false;
    }

    // --- RENDER LOGIC ---
    @SubscribeEvent
    public void onRenderOverlay(RenderGameOverlayEvent.Text event) {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc == null || mc.fontRendererObj == null) return;
        List<String> linesToRender = getLiveQuestLines();
        if (linesToRender.isEmpty()) linesToRender = apiLines; 
        if (linesToRender.isEmpty()) return;
        
        int backgroundColor = 0x90000000; 
        int padding = 2;
        int startX = 5;
        int startY = 150;
        int width = 0;
        int height = linesToRender.size() * 10;
        for (String line : linesToRender) {
            int lineWidth = mc.fontRendererObj.getStringWidth(line);
            if (lineWidth > width) width = lineWidth;
        }
        net.minecraft.client.gui.Gui.drawRect(startX - padding, startY - padding, startX + width + padding, startY + height + padding, backgroundColor);

        int y = 150;
        for (String line : linesToRender) {
            mc.fontRendererObj.drawStringWithShadow(line, 5, y, 0xFFFFFF);
            y += 10;
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
    
    // --- API/REFLECTION METHODS ---
    private void fetchQuestData() {
        Minecraft mc = Minecraft.getMinecraft(); 
        if (mc == null || mc.thePlayer == null) return;
        lastFetchTime = System.currentTimeMillis();
        String apiKey = getMeowtilsKey();
        if (apiKey.isEmpty() || apiKey.length() < 10) {
            updateApiDisplay(EnumChatFormatting.RED + "API Key not found!");
            apiLines.add(EnumChatFormatting.GRAY + "Run /meowapi <key>");
            return;
        }
        String uuid = mc.thePlayer.getUniqueID().toString().replace("-", ""); 
        executor.submit(() -> {
            try {
                URL url = new URL("https://api.hypixel.net/player?key=" + apiKey + "&uuid=" + uuid);
                HttpURLConnection connection = (HttpURLConnection) url.openConnection();
                connection.setRequestMethod("GET");
                connection.setRequestProperty("User-Agent", "MeowtilsAddon");
                if (connection.getResponseCode() == 200) {
                    InputStreamReader reader = new InputStreamReader(connection.getInputStream());
                    JsonObject json = new JsonParser().parse(reader).getAsJsonObject();
                    reader.close();
                    if (json.has("player") && !json.get("player").isJsonNull()) parseQuests(json.getAsJsonObject("player"));
                } else {
                    updateApiDisplay(EnumChatFormatting.RED + "API Error: " + connection.getResponseCode());
                }
            } catch (Exception e) {
                e.printStackTrace();
                updateApiDisplay(EnumChatFormatting.RED + "Connection Failed");
            }
        });
    }

    private String getMeowtilsKey() {
        try {
            Class<?> configClass = Class.forName("wtf.tatp.meowtils.config.cfg");
            Field instanceField = configClass.getDeclaredField("v");
            instanceField.setAccessible(true);
            Object configInstance = instanceField.get(null); 
            if (configInstance != null) {
                Field keyField = configClass.getDeclaredField("apiKey");
                keyField.setAccessible(true);
                Object keyValue = keyField.get(configInstance); 
                if (keyValue != null) return keyValue.toString();
            }
        } catch (Exception e) {}
        return "";
    }

    private void parseQuests(JsonObject player) {
        List<String> lines = new ArrayList<>();
        lines.add(EnumChatFormatting.GOLD + "-- Daily Quests (API Status) --");
        if (!player.has("quests")) {
            lines.add(EnumChatFormatting.GRAY + "No static quest data.");
            apiLines = lines;
            return;
        }
        JsonObject quests = player.getAsJsonObject("quests");
        checkQuest(quests, lines, "bedwars_daily_win", "Bedwars Win");
        checkQuest(quests, lines, "bedwars_daily_bed", "Bedwars Bed");
        apiLines = lines;
    }

    private void checkQuest(JsonObject questsData, List<String> lines, String questId, String displayName) {
        boolean completedToday = false;
        if (questsData.has(questId)) {
            JsonObject quest = questsData.getAsJsonObject(questId);
            if (quest.has("completions")) {
                JsonArray completions = quest.getAsJsonArray("completions");
                if (completions.size() > 0) {
                    long lastMs = completions.get(completions.size() - 1).getAsJsonObject().get("time").getAsLong();
                    if (System.currentTimeMillis() - lastMs < 86400000L) {
                        completedToday = true;
                    }
                }
            }
        }
        if (completedToday) lines.add(EnumChatFormatting.GREEN + "[✔] " + displayName);
        else lines.add(EnumChatFormatting.RED + "[✖] " + displayName);
    }
    
    private void updateApiDisplay(String msg) {
        List<String> l = new ArrayList<>();
        l.add(msg);
        apiLines = l;
    }

    private static class DebugCommand extends CommandBase {
        @Override public String getCommandName() { return "questdebug"; }
        @Override public String getCommandUsage(ICommandSender sender) { return "/questdebug"; }
        @Override public int getRequiredPermissionLevel() { return 0; }
        @Override public boolean canCommandSenderUseCommand(ICommandSender sender) { return true; }
        @Override public void processCommand(ICommandSender sender, String[] args) {
            AutomatedQuestTracker.debugMode = !AutomatedQuestTracker.debugMode;
            sender.addChatMessage(new ChatComponentText(EnumChatFormatting.AQUA + "[QuestHUD] Debug Mode: " + (AutomatedQuestTracker.debugMode ? EnumChatFormatting.GREEN + "ON" : EnumChatFormatting.RED + "OFF")));
        }
    }
}