package wtf.tatp.meowtils.modules.advanced;

import wtf.tatp.meowtils.gui.Module;
import wtf.tatp.meowtils.gui.values.NumberValue;
import wtf.tatp.meowtils.gui.values.BooleanValue;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.client.event.ClientChatReceivedEvent;
import net.minecraft.item.ItemStack;
import net.minecraft.init.Items;
import wtf.tatp.meowtils.config.cfg;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.minecraft.util.ChatComponentText;
import net.minecraft.scoreboard.Scoreboard;
import net.minecraft.scoreboard.ScoreObjective;
import net.minecraft.scoreboard.Score;
import net.minecraft.scoreboard.ScorePlayerTeam;
import java.util.Collection;
import java.util.ArrayList;

public class LevelFaker extends Module {
    private final NumberValue networkLevel;
    private final BooleanValue networkLevelToggle; // on/off
    private final NumberValue bedwarsLevel;
    private boolean faking;
    private int savedLevel;
    private int tick; // for throttling debug

    // Star faker state
    private TreeMap<Integer, String> starTemplates; // key: 0,100,...,5000 -> formatted tag
    // Match [123], [123✫], [123✪], etc. (optional non-digit symbol(s) and optional closing bracket)
    private static final Pattern BRACKETED_NUMBER = Pattern.compile("\\[(\\d{1,4})(?:[^0-9\\]]+)?\\]?");

    public LevelFaker() {
        // Bind to cfg so toggle/key persist: levelFakerKey & levelFaker
        super("LevelFaker", "levelFakerKey", "levelFaker", Module.Category.Advanced);
        try { this.tooltip("Hypixel Network Level Faker & Bedwars Star Faker"); } catch (Throwable ignored) {}

        // Toggle first (checkbox)
        this.networkLevelToggle = new BooleanValue("Network Level", "networkLevelEnabledBool");
        this.addBoolean(networkLevelToggle);

        // Slider: 1 - 500 (step 1) then (named to sort after the toggle)
        this.networkLevel = new NumberValue("Network Level Value", 1.0, 500.0, 1.0, null, "networkLevel", Integer.TYPE);
        this.addValue(networkLevel);

        // Bedwars Level: 1 - 5000
        this.bedwarsLevel = new NumberValue("Bedwars Level", 1.0, 5000.0, 1.0, null, "bedwarsLevel", Integer.TYPE);
        this.addValue(bedwarsLevel);
    }

    private static String stripColors(String s) {
        if (s == null) return null;
        StringBuilder out = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '§' && i + 1 < s.length()) { i++; continue; }
            out.append(c);
        }
        return out.toString();
    }

    @Override
    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (this.mc == null || this.mc.thePlayer == null) return;
        if (event.phase != TickEvent.Phase.START) return;
        tick++;
        if (!this.getState()) {
            // Ensure we restore if module toggled off
            if (faking) restoreLevel();
            return;
        }

        // Fallback: dump sidebar via Scoreboard API every 20 ticks (~1s)
        if ((tick % 20) == 0) {
            try {
                if (this.mc.theWorld != null) {
                    Scoreboard sb = this.mc.theWorld.getScoreboard();
                    if (sb != null) {
                        ScoreObjective obj = sb.getObjectiveInDisplaySlot(1);
                        if (obj != null) {
                            Collection<Score> col = sb.getSortedScores(obj);
                            ArrayList<String> lines = new ArrayList<>();
                            for (Score sc : col) {
                                if (sc == null) continue;
                                String name = sc.getPlayerName();
                                if (name == null || name.startsWith("#")) continue;
                                ScorePlayerTeam team = sb.getPlayersTeam(name);
                                String formatted = ScorePlayerTeam.formatPlayerName(team, name);
                                lines.add(formatted);
                            }
                            int count = Math.min(lines.size(), 15);
                            System.out.println("[LevelFaker] (tick) Sidebar lines=" + count + ", objective='" + obj.getDisplayName() + "'");
                            for (int i = 0; i < count; i++) {
                                int idx = lines.size() - count + i;
                                String raw = lines.get(idx);
                                String bare = stripColors(raw);
                                System.out.println("[LevelFaker] (tick) sb[" + i + "]: raw='" + raw + "' bare='" + bare + "'");
                            }
                        } else {
                            System.out.println("[LevelFaker] (tick) No sidebar objective");
                        }
                    } else {
                        System.out.println("[LevelFaker] (tick) No scoreboard");
                    }
                }
            } catch (Throwable ignored) {}
        }

        // Respect toggle: if off, ensure restore and skip
        boolean enabled = getCfgBool("networkLevelEnabledBool", true);
        boolean hasMenuCompass = hasGameMenuCompass();
        if (hasMenuCompass) {
            if (!enabled) { if (faking) restoreLevel(); return; }
            int target = getCfgInt("networkLevel", 1);
            if (!faking) {
                savedLevel = this.mc.thePlayer.experienceLevel;
                faking = true;
            }
            this.mc.thePlayer.experienceLevel = target;
        } else {
            if (faking) restoreLevel();
        }
    }

    // Intercept received chat and rewrite our own messages' [NUMBER] token to star tag
    @SubscribeEvent
    public void onClientChatReceived(ClientChatReceivedEvent event) {
        try {
            if (!this.getState()) return;
            if (this.mc == null || this.mc.thePlayer == null) return;
            if (event == null || event.message == null) return;

            String self = this.mc.thePlayer.getName();
            String formatted = event.message.getFormattedText();
            if (formatted == null) return;
            // Only touch lines that contain our player name to approximate "messages we sent"
            if (!formatted.contains(self)) return;

            ensureStarTemplates();
            Matcher m = BRACKETED_NUMBER.matcher(formatted);
            if (!m.find()) return;

            int level = getCfgInt("bedwarsLevel", 1);
            if (level < 0) level = 0; if (level > 5000) level = 5000;
            String tag = buildStarTag(level);
            if (tag == null) return;

            StringBuilder sb = new StringBuilder();
            sb.append(formatted, 0, m.start());
            sb.append(tag);
            sb.append(formatted.substring(m.end() > m.start() ? m.end() : m.start()));
            event.message = new ChatComponentText(sb.toString());
        } catch (Throwable ignored) {}
    }

    private void ensureStarTemplates() {
        if (starTemplates != null) return;
        starTemplates = new TreeMap<>();
        try {
            InputStream is = LevelFaker.class.getClassLoader().getResourceAsStream("meowtilsaddons_bedwars_stars.txt");
            if (is == null) return;
            try (BufferedReader br = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
                String line;
                while ((line = br.readLine()) != null) {
                    line = line.trim();
                    if (line.isEmpty() || line.startsWith("#")) continue;
                    int eq = line.indexOf('=');
                    if (eq <= 0) continue;
                    String k = line.substring(0, eq).trim();
                    String v = line.substring(eq + 1).trim();
                    try {
                        int key = Integer.parseInt(k);
                        starTemplates.put(key, v);
                    } catch (NumberFormatException ignored) {}
                }
            }
        } catch (Throwable ignored) {}
    }

    // Build a colored star tag for an arbitrary level using the nearest 100-template's coloring
    private String buildStarTag(int level) {
        if (starTemplates == null || starTemplates.isEmpty()) return null;
        Map.Entry<Integer, String> floor = starTemplates.floorEntry(level - (level % 100));
        if (floor == null) floor = starTemplates.firstEntry();
        String template = floor.getValue();
        if (template == null) return null;

        // Extract bracket prefix and suffix
        int open = template.indexOf('[');
        int close = template.lastIndexOf(']');
        if (open < 0 || close < 0 || close <= open) return template;
        String prefix = template.substring(0, open + 1); // includes '['
        String inside = template.substring(open + 1, close); // digits + symbol
        String suffix = template.substring(close); // includes ']'

        // Split inside into digit-color segments and trailing symbol segment
        int symIdx = lastSymbolIndex(inside);
        if (symIdx < 0) return template;
        String digitsColored = inside.substring(0, symIdx);
        String symbolAndColors = inside.substring(symIdx); // contains color codes + symbol

        String digits = String.valueOf(level);
        String rebuiltDigits = recolorDigits(digitsColored, digits);

        return prefix + rebuiltDigits + symbolAndColors + suffix;
    }

    // Find the index where the symbol (✫,✪,⚝,✥) begins in the inside string
    private static int lastSymbolIndex(String s) {
        int idx = -1;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '✫' || c == '✪' || c == '⚝' || c == '✥') { idx = i; break; }
        }
        return idx;
    }

    // Recolor arbitrary digits using the section codes sequence present in the template's digit area
    // Example: template digitsColored = "§f1§f0§f0" and digits = "123" -> "§f1§f2§f3"
    private static String recolorDigits(String digitsColored, String digits) {
        StringBuilder out = new StringBuilder();
        int d = 0;
        for (int i = 0; i < digitsColored.length(); ) {
            char c = digitsColored.charAt(i);
            if (c == '§' && i + 1 < digitsColored.length()) {
                // copy color/format code
                out.append(c).append(digitsColored.charAt(i + 1));
                i += 2;
                // If next template char is a digit, replace it; otherwise leave it for next loop
                if (i < digitsColored.length() && Character.isDigit(digitsColored.charAt(i))) {
                    char next = (d < digits.length()) ? digits.charAt(d++) : '0';
                    i += 1; // consume the template digit we replaced
                    out.append(next);
                }
            } else {
                // Non color code; if it's a digit in template, replace with our next digit
                if (Character.isDigit(c)) {
                    char next = (d < digits.length()) ? digits.charAt(d++) : '0';
                    out.append(next);
                } else {
                    out.append(c);
                }
                i += 1;
            }
        }
        // If more digits remain, append them with the last seen color code
        if (d < digits.length()) {
            char lastCode = 'f';
            for (int i = out.length() - 2; i >= 0; i--) {
                if (out.charAt(i) == '§') { lastCode = out.charAt(i + 1); break; }
            }
            while (d < digits.length()) {
                out.append('§').append(lastCode).append(digits.charAt(d++));
            }
        }
        return out.toString();
    }

    private void restoreLevel() {
        try { this.mc.thePlayer.experienceLevel = savedLevel; } catch (Throwable ignored) {}
        faking = false;
    }

    private boolean hasGameMenuCompass() {
        try {
            ItemStack[] inv = this.mc.thePlayer.inventory.mainInventory;
            if (inv == null) return false;
            for (ItemStack s : inv) {
                if (s == null) continue;
                if (s.getItem() == Items.compass) {
                    String name = s.getDisplayName();
                    if (name == null) continue;
                    // Match exact or relaxed variant
                    String n = name.toLowerCase(java.util.Locale.ROOT);
                    if (n.contains("game menu") && n.contains("right click")) return true;
                    if ("game menu (right click)".equalsIgnoreCase(name)) return true;
                }
            }
        } catch (Throwable ignored) {}
        return false;
    }

    private static int getCfgInt(String field, int def) {
        try {
            java.lang.reflect.Field f = cfg.v.getClass().getField(field);
            Object val = f.get(cfg.v);
            if (val instanceof Number) return ((Number) val).intValue();
            if (val != null) return Integer.parseInt(String.valueOf(val));
            return def;
        } catch (Throwable t) { return def; }
    }

    private static boolean getCfgBool(String field, boolean def) {
        try {
            java.lang.reflect.Field f = cfg.v.getClass().getField(field);
            Object val = f.get(cfg.v);
            if (val instanceof Boolean) return (Boolean) val;
            if (val != null) return Boolean.parseBoolean(String.valueOf(val));
            return def;
        } catch (Throwable t) { return def; }
    }
}
