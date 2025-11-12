package com.github.tewxx.meowtilsaddons.inject;

import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.fml.common.Loader;

public final class ModuleBootstrap {
    private boolean done;

    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent e) {
        if (done) return;
        if (e.phase != TickEvent.Phase.END) return;
        try {
            if (!Loader.isModLoaded("meowtils")) return;
            ModuleInjector.injectStatic(Class.forName("wtf.tatp.meowtils.gui.ModuleManager"));
            tryAppendAutoFish();
            done = true;
        } catch (Throwable ignored) { }
    }

    private static void tryAppendAutoFish() {
        try {
            Class<?> mm = Class.forName("wtf.tatp.meowtils.gui.ModuleManager");
            java.util.List list = null;
            for (java.lang.reflect.Field f : mm.getDeclaredFields()) {
                int mod = f.getModifiers();
                if (!java.lang.reflect.Modifier.isStatic(mod)) continue;
                Class<?> ft = f.getType();
                if (java.util.List.class.isAssignableFrom(ft) || java.util.Collection.class.isAssignableFrom(ft)) {
                    f.setAccessible(true);
                    Object val = f.get(null);
                    if (val instanceof java.util.List) { list = (java.util.List) val; break; }
                }
            }
            if (list != null) {
                Class<?> af = Class.forName("com.github.tewxx.meowtilsaddons.modules.utility.AutoFish");
                boolean present = false;
                for (Object o : list) {
                    if (o != null && o.getClass().getName().equals(af.getName())) { present = true; break; }
                }
                if (!present) {
                    Object inst = af.getDeclaredConstructor().newInstance();
                    list.add(inst);
                    System.out.println("[MeowtilsAddons] Bootstrap appended AutoFish into ModuleManager modules list");
                }
            }
        } catch (Throwable ignored) { }
    }
}
