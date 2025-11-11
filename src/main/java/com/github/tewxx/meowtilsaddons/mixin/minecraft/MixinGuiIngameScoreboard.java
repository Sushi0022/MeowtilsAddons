package com.github.tewxx.meowtilsaddons.mixin.minecraft;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.GuiIngame;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.scoreboard.Score;
import net.minecraft.scoreboard.ScoreObjective;
import net.minecraft.scoreboard.ScorePlayerTeam;
import net.minecraft.scoreboard.Scoreboard;
import net.minecraft.scoreboard.Team;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import wtf.tatp.meowtils.config.cfg;
import wtf.tatp.meowtils.modules.advanced.LevelFaker;

import java.util.ArrayList;
import java.util.List;

@Mixin(value = GuiIngame.class, remap = false)
public abstract class MixinGuiIngameScoreboard {

    @Inject(method = "renderScoreboard(Lnet/minecraft/scoreboard/ScoreObjective;Lnet/minecraft/client/gui/ScaledResolution;)V", at = @At("HEAD"), cancellable = true)
    private void meowtilsaddons$injectRenderScoreboard(ScoreObjective objective, ScaledResolution sr, CallbackInfo ci) {
        try {
            if (objective == null) return;
            System.out.println("[Mixin] GuiIngame renderScoreboard inject fired. Objective='" + objective.getDisplayName() + "'");
            Minecraft mc = Minecraft.getMinecraft();
            FontRenderer fr = mc.fontRendererObj;
            Scoreboard sb = objective.getScoreboard();
            List<Score> scores = new ArrayList<>();
            for (Score s : sb.getSortedScores(objective)) {
                String name = s.getPlayerName();
                if (name != null && !name.startsWith("#") && scores.size() < 15) scores.add(s);
            }
            int width = fr.getStringWidth(objective.getDisplayName());
            List<String> lines = new ArrayList<>();
            boolean foundYOU = false;
            for (Score s : scores) {
                Team team = sb.getPlayersTeam(s.getPlayerName());
                String line = ScorePlayerTeam.formatPlayerName(team, s.getPlayerName());
                if (!foundYOU && line != null) {
                    String bareLine = strip(line);
                    if (bareLine != null && bareLine.toUpperCase(java.util.Locale.ROOT).contains("YOU")) {
                        foundYOU = true;
                    }
                }
                String bare = strip(line);
                if (cfgBool("levelFaker", false) && bare != null && bare.trim().toLowerCase(java.util.Locale.ROOT).startsWith("level:")) {
                    String tag = buildStarTag(cfgInt("bedwarsLevel", 1));
                    if (tag != null) line = "§fLevel: " + removeBrackets(tag);
                    System.out.println("[Mixin] (GuiIngame) Replaced Level line to -> " + line);
                }
                lines.add(line);
                width = Math.max(width, fr.getStringWidth(line));
            }
            // Update in-game flag based on presence of 'YOU'. XP is handled in LevelFaker tick using compass detection.
            LevelFaker.IN_BEDWARS = foundYOU;
            // Center vertically using bottom-up layout like CustomSidebar
            int sidebarWidth = width;
            int sidebarHeight = lines.size() * fr.FONT_HEIGHT;
            int sidebarX = sr.getScaledWidth() - sidebarWidth - 3; // keep right side
            int sidebarY = (sr.getScaledHeight() + sidebarHeight) / 2; // center baseline
            System.out.println("[Mixin] GuiIngame center: screenH=" + sr.getScaledHeight() + ", sidebarH=" + sidebarHeight + ", sidebarY=" + sidebarY);

            int bg = 0x4F000000; // semi-transparent black
            int rightX = sidebarX + sidebarWidth + 2;

            // Draw lines bottom-up from center baseline
            int index = 0;
            int lastScoreY = sidebarY;
            for (int i = 0; i < lines.size(); i++) {
                index++;
                String line = lines.get(i); // normal order, compute bottom-up positions
                int y = sidebarY - index * fr.FONT_HEIGHT;
                lastScoreY = y;
                net.minecraft.client.gui.Gui.drawRect(sidebarX - 2, y, rightX, y + fr.FONT_HEIGHT, bg);
                fr.drawStringWithShadow(line, (float) sidebarX, (float) y, 0xFFFFFF);
            }

            // Header above the top line
            int headerTop = lastScoreY - fr.FONT_HEIGHT - 1;
            int headerBottom = lastScoreY - 1;
            net.minecraft.client.gui.Gui.drawRect(sidebarX - 2, headerTop, rightX, headerBottom, bg);
            net.minecraft.client.gui.Gui.drawRect(sidebarX - 2, headerBottom, rightX, headerBottom + 1, bg);
            String title = objective.getDisplayName();
            fr.drawStringWithShadow(title, (float)(sidebarX + (sidebarWidth - fr.getStringWidth(title)) / 2), (float) (headerTop), 0xFFFFFF);
            ci.cancel();
        } catch (Throwable ignored) {}
    }

    private static String strip(String s) {
        if (s == null) return null;
        StringBuilder out = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '§' && i + 1 < s.length()) { i++; continue; }
            out.append(c);
        }
        return out.toString();
    }

    private static int cfgInt(String field, int def) {
        try {
            java.lang.reflect.Field f = cfg.v.getClass().getField(field);
            Object val = f.get(cfg.v);
            if (val instanceof Number) return ((Number) val).intValue();
            if (val != null) return Integer.parseInt(String.valueOf(val));
            return def;
        } catch (Throwable t) { return def; }
    }

    private static boolean cfgBool(String field, boolean def) {
        try {
            java.lang.reflect.Field f = cfg.v.getClass().getField(field);
            Object val = f.get(cfg.v);
            if (val instanceof Boolean) return (Boolean) val;
            if (val != null) return Boolean.parseBoolean(String.valueOf(val));
            return def;
        } catch (Throwable t) { return def; }
    }

    // Reuse LevelFaker's tag builder via resource
    private static final java.util.TreeMap<Integer, String> MT_TEMPLATES = new java.util.TreeMap<>();
    private static boolean MT_LOADED = false;
    private static void ensureTemplates() {
        if (MT_LOADED) return; MT_LOADED = true;
        try {
            java.io.InputStream is = MixinGuiIngameScoreboard.class.getClassLoader().getResourceAsStream("meowtilsaddons_bedwars_stars.txt");
            if (is == null) return;
            try (java.io.BufferedReader br = new java.io.BufferedReader(new java.io.InputStreamReader(is, java.nio.charset.StandardCharsets.UTF_8))) {
                String line;
                while ((line = br.readLine()) != null) {
                    line = line.trim(); if (line.isEmpty() || line.startsWith("#")) continue;
                    int eq = line.indexOf('='); if (eq <= 0) continue;
                    String k = line.substring(0, eq).trim(); String v = line.substring(eq + 1).trim();
                    try { MT_TEMPLATES.put(Integer.parseInt(k), v); } catch (NumberFormatException ignored) {}
                }
            }
        } catch (Throwable ignored) {}
    }
    private static String buildStarTag(int level) {
        ensureTemplates(); if (MT_TEMPLATES.isEmpty()) return null;
        if (level < 0) level = 0; if (level > 5000) level = 5000;
        java.util.Map.Entry<Integer, String> floor = MT_TEMPLATES.floorEntry(level - (level % 100));
        if (floor == null) floor = MT_TEMPLATES.firstEntry();
        String template = floor.getValue(); if (template == null) return null;
        int open = template.indexOf('['), close = template.lastIndexOf(']');
        if (open < 0 || close < 0 || close <= open) return template;
        String prefix = template.substring(0, open + 1);
        String inside = template.substring(open + 1, close);
        String suffix = template.substring(close);
        int symIdx = symbolIndex(inside); if (symIdx < 0) return template;
        String digitsColored = inside.substring(0, symIdx);
        String symbolAndColors = inside.substring(symIdx);
        String digits = String.valueOf(level);
        String rebuiltDigits = recolorDigits(digitsColored, digits);
        return prefix + rebuiltDigits + symbolAndColors + suffix;
    }
    private static int symbolIndex(String s) {
        for (int i = 0; i < s.length(); i++) { char c = s.charAt(i); if (c == '✫' || c == '✪' || c == '⚝' || c == '✥') return i; } return -1;
    }
    private static String recolorDigits(String digitsColored, String digits) {
        StringBuilder out = new StringBuilder(); int d = 0;
        for (int i = 0; i < digitsColored.length(); ) { char c = digitsColored.charAt(i);
            if (c == '§' && i + 1 < digitsColored.length()) { out.append(c).append(digitsColored.charAt(i + 1)); i += 2;
                if (i < digitsColored.length() && Character.isDigit(digitsColored.charAt(i))) { char next = (d < digits.length()) ? digits.charAt(d++) : '0'; i += 1; out.append(next);} }
            else { if (Character.isDigit(c)) { char next = (d < digits.length()) ? digits.charAt(d++) : '0'; out.append(next);} else { out.append(c);} i += 1; }
        }
        if (d < digits.length()) { char lastCode = 'f'; for (int i = out.length() - 2; i >= 0; i--) { if (out.charAt(i) == '§') { lastCode = out.charAt(i + 1); break; } }
            while (d < digits.length()) { out.append('§').append(lastCode).append(digits.charAt(d++)); } }
        return out.toString();
    }

    private static String removeBrackets(String s) {
        if (s == null) return null;
        // Remove ASCII and fullwidth brackets anywhere in the string
        return s
            .replace("[", "")
            .replace("]", "")
            .replace("［", "") // U+FF3B fullwidth [
            .replace("］", ""); // U+FF3D fullwidth ]
    }
}
