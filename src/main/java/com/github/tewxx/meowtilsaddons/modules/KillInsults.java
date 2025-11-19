package com.github.tewxx.meowtilsaddons.modules;

import net.minecraftforge.client.event.ClientChatReceivedEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraft.client.Minecraft;
import wtf.tatp.meowtils.gui.Module;
import wtf.tatp.meowtils.gui.values.ArrayValue;
import java.util.Arrays;
import java.util.Locale;
import java.util.Random;
import java.util.List;
import java.util.ArrayList;
import java.io.*;
import java.nio.charset.StandardCharsets;

public class KillInsults extends Module {
    private final ArrayValue modeArray;
    private final Random rng = new Random();
    private int nextInsultIndex = 0;
    private File insultsFile;
    private long insultsLastLoaded = 0L;
    private final List<String> insults = new ArrayList<>();

    public KillInsults() {
        super("KillInsults", "killInsultsKey", "killInsults", Module.Category.Advanced);
        try { this.tooltip("Sends a message whenever you kill someone\n§5/insults to open txt file to add custom lines"); } catch (Throwable ignored) {}

        this.modeArray = new ArrayValue("Mode", Arrays.asList("Message", "Shout", "Normal"), "killInsultsMode");
        this.addArray(modeArray);


        try {
            File mcDir = Minecraft.getMinecraft().mcDataDir;
            File cfgDir = new File(mcDir, "config/MeowtilsAddons");
            if (!cfgDir.exists()) cfgDir.mkdirs();
            this.insultsFile = new File(cfgDir, "killinsults.txt");
        } catch (Throwable ignored) {}
    }

    @SubscribeEvent
    public void onChat(ClientChatReceivedEvent event) {
        if (!this.getState()) return;
        if (this.mc == null || this.mc.thePlayer == null) return;
        if (event == null || event.message == null) return;

        String formatted = event.message.getFormattedText();
        if (formatted == null || formatted.isEmpty()) return;
        String bare = stripColors(formatted);
        if (bare == null || bare.isEmpty()) return;

        String self = this.mc.thePlayer.getName();
        if (self == null || self.isEmpty()) return;

        String lower = bare.toLowerCase(Locale.ROOT);

        String token = " by " + self.toLowerCase(Locale.ROOT);
        int idx = lower.indexOf(token);
        if (idx < 0) return;

        String victim = extractVictim(bare, idx);
        String insult = pickInsult(victim);

        if (insult == null || insult.isEmpty()) return;
        String mode = modeArray.getValue();
        String toSend;
        if ("Message".equalsIgnoreCase(mode)) {
            if (victim != null && !victim.isEmpty()) toSend = "/msg " + victim + " " + insult;
            else toSend = insult;
        } else if ("Shout".equalsIgnoreCase(mode)) {
            toSend = "/shout " + insult;
        } else {
            toSend = insult;
        }

        try { this.mc.thePlayer.sendChatMessage(toSend); } catch (Throwable ignored) {}

    }

    private static String stripColors(String s) {
        StringBuilder out = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '§' && i + 1 < s.length()) { i++; continue; }
            out.append(c);
        }
        return out.toString();
    }

    private static String extractVictim(String bare, int byIndexLowerBound) {
        try {
            String left = bare.substring(0, Math.max(0, byIndexLowerBound)).trim();
            left = left.replaceAll("^\\[[^\\]]+\\]\\s*", "").trim();
            String[] cues = new String[]{" was ", " got ", " has ", " fell ", " died ", " final kill"};
            int cut = -1;
            String ll = left.toLowerCase(Locale.ROOT);
            for (String cue : cues) {
                int i = ll.lastIndexOf(cue);
                if (i > cut) cut = i;
            }
            String candidate = (cut > 0 ? left.substring(0, cut) : left).trim();
            java.util.regex.Matcher m = java.util.regex.Pattern.compile("([A-Za-z0-9_]{3,16})$").matcher(candidate);
            if (m.find()) return m.group(1);
            String[] parts = candidate.split(" ");
            return parts.length > 0 ? parts[parts.length - 1] : null;
        } catch (Throwable t) {
            return null;
        }
    }

    private String pickInsult(String victim) {
        ensureInsultsLoaded();
        String base;
        if (!insults.isEmpty()) {
            if (nextInsultIndex >= insults.size()) nextInsultIndex = 0;
            base = insults.get(nextInsultIndex);
            nextInsultIndex++;
        } else {
            return null;
        }
        String namePart = (victim != null && !victim.isEmpty()) ? (" " + victim) : "";
        return base.replace("{n}", namePart);
    }

    private void ensureInsultsLoaded() {
        try {
            if (insultsFile == null) return;
            long lm = insultsFile.exists() ? insultsFile.lastModified() : -1L;
            if (lm <= 0) return;
            if (!insults.isEmpty() && insultsLastLoaded >= lm) return;
            insults.clear();
            nextInsultIndex = 0;
            try (BufferedReader br = new BufferedReader(new InputStreamReader(new FileInputStream(insultsFile), StandardCharsets.UTF_8))) {
                String line;
                while ((line = br.readLine()) != null) {
                    String s = line.trim();
                    if (s.isEmpty()) continue;
                    if (s.startsWith("#") || s.startsWith("//")) continue;
                    insults.add(s);
                }
            }
            insultsLastLoaded = System.currentTimeMillis();
        } catch (Throwable ignored) {}
    }
}
