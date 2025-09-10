package com.example;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class InjectionState {
    public static volatile boolean meowtilsInjectEnabled = false;
    // Holds all runtime injection lines to append at the target location
    public static final List<String> meowtilsInjectMessages = Collections.synchronizedList(new ArrayList<String>());
    // Holds module names requested via /injection injectmodule <name>
    public static final List<String> meowtilsInjectedModuleNames = new ArrayList<String>();
    // Maps module display name -> Category string (e.g., "Utility", "Hypixel"). Defaults handled at usage sites.
    public static final Map<String, String> meowtilsInjectedModuleCategories = new ConcurrentHashMap<String, String>();

    // Normalize a module display name: strip color codes (§x and &x), trim, lowercase
    public static String normalizeName(String s) {
        if (s == null) return "";
        String noColors = s.replaceAll("\u00A7.", "").replaceAll("&.", "");
        return noColors.trim().toLowerCase(java.util.Locale.ROOT);
    }

    // Latest live instance per injected module name (normalized)
    public static final ConcurrentHashMap<String, Object> latestInjectedModuleInstances = new ConcurrentHashMap<String, Object>();
}
