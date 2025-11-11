package wtf.tatp.meowtils.modules.utility;

import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import wtf.tatp.meowtils.gui.Module;

/**
 * Empty AutoFish module placed under Meowtils namespace so ModuleManager
 * can reference it directly via mixin. It is intentionally minimal and
 * only exists to appear in the GUI and for config persistence tests.
 */
public class AutoFish extends Module {
    public AutoFish() {
        super("AutoFish", "autoFishKey", "autoFish", Module.Category.Utility);
        try { this.tooltip("Injected test module (no behavior)"); } catch (Throwable ignored) {}
    }

    @Override
    @SubscribeEvent
    public void onRenderTick(TickEvent.RenderTickEvent event) {
        // no behavior; visibility test only
    }
}
