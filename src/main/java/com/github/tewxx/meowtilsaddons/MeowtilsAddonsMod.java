package com.github.tewxx.meowtilsaddons;

import net.minecraftforge.client.ClientCommandHandler;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.common.MinecraftForge;

import com.github.tewxx.meowtilsaddons.inject.cmd.MTADumpCommand;
import com.github.tewxx.meowtilsaddons.inject.cmd.MTADumpJarCommand;
import com.github.tewxx.meowtilsaddons.inject.cmd.MTADumpJarPathCommand;
import com.github.tewxx.meowtilsaddons.inject.ModuleBootstrap;

@Mod(modid = "meowtilsaddons", name = "MeowtilsAddons", version = "1.0.0", clientSideOnly = true, acceptedMinecraftVersions = "[1.8.9]")
public class MeowtilsAddonsMod {

    @Mod.EventHandler
    public void init(FMLInitializationEvent event) {
        // Register client-only command to trigger a manual dump
        ClientCommandHandler.instance.registerCommand(new MTADumpCommand());
        ClientCommandHandler.instance.registerCommand(new MTADumpJarCommand());
        ClientCommandHandler.instance.registerCommand(new MTADumpJarPathCommand());

        // Ensure our module is appended even if static mixins miss the window
        MinecraftForge.EVENT_BUS.register(new ModuleBootstrap());
    }
}
