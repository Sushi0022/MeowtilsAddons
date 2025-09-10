package com.example.mixin.meowtils;

import net.minecraft.client.Minecraft;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.FMLCommonHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// Meowtils classes
// Avoid direct imports of Meowtils classes; use reflection so we don't need them on compile classpath

@Pseudo
@Mixin(targets = "wtf.tatp.meowtils.gui.Module", remap = false)
public abstract class MixinModuleSetState {

    private static final ThreadLocal<Boolean> REDIRECT_GUARD = new ThreadLocal<Boolean>() {
        @Override protected Boolean initialValue() { return Boolean.FALSE; }
    };
    private static final java.util.concurrent.ConcurrentHashMap<String, Long> RECENT_TOGGLES = new java.util.concurrent.ConcurrentHashMap<String, Long>();

    // If a stale instance is toggled, reroute the toggle to the latest instance and cancel original
    @Inject(method = "setState(Z)V", at = @At("HEAD"), cancellable = true, require = 0)
    private void onSetStateHead(boolean state, CallbackInfo ci) {
        if (Boolean.TRUE.equals(REDIRECT_GUARD.get())) return;
        Object self = this;
        try {
            Class<?> cls = self.getClass();
            // Only for injected modules with our marker
            try { cls.getField("INJECTOR_TAG"); } catch (NoSuchFieldException nsfe) { return; }

            // Compute display name to index latest instance
            String displayName = null;
            try {
                java.lang.reflect.Field f = cls.getField("modulename");
                Object v = f.get(null);
                displayName = v == null ? null : String.valueOf(v);
            } catch (Throwable ignored) {}
            if (displayName == null) {
                try { displayName = String.valueOf(cls.getMethod("getName").invoke(self)); } catch (Throwable ignored) {}
            }
            if (displayName == null) return;

            String norm = com.example.InjectionState.normalizeName(displayName);
            // Debounce: if the same target state for this module was processed very recently, drop it
            try {
                String sig = norm + "|" + state;
                long now = System.currentTimeMillis();
                Long last = RECENT_TOGGLES.get(sig);
                if (last != null && (now - last) < 300L) {
                    ci.cancel();
                    return;
                }
                RECENT_TOGGLES.put(sig, now);
            } catch (Throwable ignored) {}
            Object latest = com.example.InjectionState.latestInjectedModuleInstances.get(norm);

            // If this is already the latest instance, suppress duplicate transitions when state is unchanged
            if (latest == null || latest == self) {
                Boolean cur = tryGetState(self);
                if (cur != null && cur.booleanValue() == state) {
                    ci.cancel();
                    return;
                }
                return; // continue normal flow
            }

            if (latest != null && latest != self) {
                // Redirect to latest instance
                try {
                    REDIRECT_GUARD.set(Boolean.TRUE);
                    // Only call setState on latest if its current state differs
                    Boolean cur = tryGetState(latest);
                    if (cur == null || cur.booleanValue() != state) {
                        latest.getClass().getMethod("setState", boolean.class).invoke(latest, state);
                    }
                } catch (Throwable ignored) {
                } finally {
                    REDIRECT_GUARD.set(Boolean.FALSE);
                }
                try {
                    System.out.println("[MeowtilsInjectors][DEBUG] Redirected toggle from stale instance " + cls.getClassLoader() + " to latest instance " + latest.getClass().getClassLoader());
                } catch (Throwable ignored) {}
                // Best-effort: sync stale instance's internal enabled flag with desired state so GUI stops thinking it's different
                try { syncEnabledFlag(self, state); } catch (Throwable ignored) {}
                // Cancel original to prevent stale instance from toggling/printing
                ci.cancel();
            }
        } catch (Throwable ignoredOuter) {
        }
    }

    // Best-effort reflect current enabled state of a module; tries common method names
    private static Boolean tryGetState(Object module) {
        if (module == null) return null;
        try {
            Class<?> c = module.getClass();
            try { return (Boolean) c.getMethod("getState").invoke(module); } catch (Throwable ignored) {}
            try { return (Boolean) c.getMethod("isEnabled").invoke(module); } catch (Throwable ignored) {}
            try { return (Boolean) c.getMethod("isToggled").invoke(module); } catch (Throwable ignored) {}
        } catch (Throwable ignoredOuter) {}
        return null;
    }

    // Best-effort: synchronize an internal boolean flag so external caches stop thinking the stale instance is in a different state
    private static void syncEnabledFlag(Object module, boolean state) {
        if (module == null) return;
        try {
            Class<?> c = module.getClass();
            // Common field names
            String[] fields = new String[] {"enabled", "toggled", "state"};
            for (String fn : fields) {
                try {
                    java.lang.reflect.Field f = c.getDeclaredField(fn);
                    if (f.getType() == boolean.class || f.getType() == Boolean.class) {
                        f.setAccessible(true);
                        f.set(module, state);
                        return;
                    }
                } catch (Throwable ignored) {}
            }
            // Common setter names
            String[] methods = new String[] {"setEnabled", "setToggled"};
            for (String mn : methods) {
                try {
                    java.lang.reflect.Method m = c.getMethod(mn, boolean.class);
                    m.invoke(module, state);
                    return;
                } catch (Throwable ignored) {}
            }
        } catch (Throwable ignoredOuter) {}
    }

    // Also handle callers that invoke toggle() directly
    @Inject(method = "toggle()V", at = @At("HEAD"), cancellable = true, require = 0)
    private void onToggleHead(CallbackInfo ci) {
        if (Boolean.TRUE.equals(REDIRECT_GUARD.get())) return;
        Object self = this;
        try {
            Class<?> cls = self.getClass();
            // Only for injected modules
            try { cls.getField("INJECTOR_TAG"); } catch (NoSuchFieldException nsfe) { return; }

            String displayName = null;
            try {
                java.lang.reflect.Field f = cls.getField("modulename");
                Object v = f.get(null);
                displayName = v == null ? null : String.valueOf(v);
            } catch (Throwable ignored) {}
            if (displayName == null) {
                try { displayName = String.valueOf(cls.getMethod("getName").invoke(self)); } catch (Throwable ignored) {}
            }
            if (displayName == null) return;

            String norm = com.example.InjectionState.normalizeName(displayName);
            // Debounce toggle() bursts
            try {
                String sig = norm + "|toggle";
                long now = System.currentTimeMillis();
                Long last = RECENT_TOGGLES.get(sig);
                if (last != null && (now - last) < 300L) {
                    ci.cancel();
                    return;
                }
                RECENT_TOGGLES.put(sig, now);
            } catch (Throwable ignored) {}
            Object latest = com.example.InjectionState.latestInjectedModuleInstances.get(norm);
            if (latest != null && latest != self) {
                try {
                    REDIRECT_GUARD.set(Boolean.TRUE);
                    latest.getClass().getMethod("toggle").invoke(latest);
                } catch (Throwable ignored) {
                } finally {
                    REDIRECT_GUARD.set(Boolean.FALSE);
                }
                try {
                    System.out.println("[MeowtilsInjectors][DEBUG] Redirected toggle() from stale instance " + cls.getClassLoader() + " to latest instance " + latest.getClass().getClassLoader());
                } catch (Throwable ignored) {}
                // Sync stale instance's internal flag to match latest after toggle
                try {
                    Boolean latestState = tryGetState(latest);
                    if (latestState != null) syncEnabledFlag(self, latestState.booleanValue());
                } catch (Throwable ignored) {}
                ci.cancel();
            }
        } catch (Throwable ignoredOuter) {}
    }

    @Inject(method = "setState(Z)V", at = @At("RETURN"), require = 0)
    private void onSetState(boolean state, CallbackInfo ci) {
        Object self = this;
        try {
            Class<?> cls = self.getClass();

            // Only act on injected modules that expose our marker field
            try {
                cls.getField("INJECTOR_TAG");
            } catch (NoSuchFieldException nsfe) {
                return; // not an injected module
            }

            // Determine display name (prefer static modulename if present; fallback to getName())
            String displayName = null;
            try {
                java.lang.reflect.Field f = cls.getField("modulename");
                Object v = f.get(null);
                displayName = v == null ? null : String.valueOf(v);
            } catch (Throwable ignored) {}
            if (displayName == null) {
                try {
                    displayName = String.valueOf(cls.getMethod("getName").invoke(self));
                } catch (Throwable ignored) {
                    displayName = "Injected";
                }
            }

            // Register/unregister event buses like native modules
            try {
                if (state) {
                    MinecraftForge.EVENT_BUS.register(self);
                    FMLCommonHandler.instance().bus().register(self);
                } else {
                    MinecraftForge.EVENT_BUS.unregister(self);
                    FMLCommonHandler.instance().bus().unregister(self);
                }
            } catch (Throwable ignored) {}

            // Toggle notification like "[Meow] Module: On/Off" using reflection
            try {
                if (Minecraft.getMinecraft() != null && Minecraft.getMinecraft().thePlayer != null) {
                    // Debug: when enabling, print classloader and code source to verify which definition is active
                    if (state) {
                        try {
                            ClassLoader cl = cls.getClassLoader();
                            java.security.ProtectionDomain pd = cls.getProtectionDomain();
                            java.net.URL src = pd == null || pd.getCodeSource() == null ? null : pd.getCodeSource().getLocation();
                            String mark = null;
                            try {
                                java.lang.reflect.Field fVer = cls.getField("VERSION");
                                Object v = fVer.get(null);
                                mark = v == null ? null : String.valueOf(v);
                            } catch (Throwable ignored) {}
                            try {
                                java.lang.reflect.Field fMark = cls.getField("RUNTIME_MARK");
                                Object v2 = fMark.get(null);
                                String m2 = v2 == null ? null : String.valueOf(v2);
                                mark = mark == null ? m2 : (mark + "," + m2);
                            } catch (Throwable ignored) {}
                            System.out.println("[MeowtilsInjectors][DEBUG] onSetState ENABLED | moduleClass=" + cls + 
                                    " | loader=" + cl + " | codeSource=" + src + (mark == null ? "" : (" | mark=" + mark)));
                        } catch (Throwable ignored) {}
                    }
                    boolean notify = false;
                    try {
                        Class<?> cfgCls = Class.forName("wtf.tatp.meowtils.config.ConfigHandler");
                        Object inst = cfgCls.getField("INSTANCE").get(null);
                        java.lang.reflect.Field fNotify = cfgCls.getField("toggleNotifications");
                        notify = fNotify.getBoolean(inst);
                    } catch (Throwable ignored) {}

                    if (notify) {
                        Class<?> meowCls = Class.forName("wtf.tatp.meowtils.Meowtils");
                        String onMsg = String.valueOf(meowCls.getField("onMessage").get(null));
                        String offMsg = String.valueOf(meowCls.getField("offMessage").get(null));
                        String suffix = state ? onMsg : offMsg;
                        java.lang.reflect.Method addMsg = meowCls.getMethod("addMessage", String.class);
                        addMsg.invoke(null, displayName + suffix);
                    }
                }
            } catch (Throwable ignored) {}
        } catch (Throwable ignoredOuter) {
        }
    }
}
