package com.github.tewxx.meowtilsaddons.inject;

import java.lang.ref.WeakReference;

public final class ModuleManagerRef {
    private static volatile WeakReference<Object> INSTANCE;

    private ModuleManagerRef() {}

    public static void set(Object instance) {
        if (instance == null) return;
        INSTANCE = new WeakReference<>(instance);
    }

    public static Object get() {
        return INSTANCE != null ? INSTANCE.get() : null;
    }
}
