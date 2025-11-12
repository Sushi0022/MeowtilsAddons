package com.github.tewxx.meowtilsaddons;

import net.minecraftforge.client.ClientCommandHandler;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.common.MinecraftForge;

import com.github.tewxx.meowtilsaddons.inject.ModuleBootstrap;
import com.github.tewxx.meowtilsaddons.commands.BedwarsLevelCommand;
import com.github.tewxx.meowtilsaddons.commands.NetworkLevelCommand;

@Mod(modid = "meowtilsaddons", name = "MeowtilsAddons", version = "1.0.0", clientSideOnly = true, acceptedMinecraftVersions = "[1.8.9]")
public class MeowtilsAddonsMod {

    @Mod.EventHandler
    public void init(FMLInitializationEvent event) {
        ClientCommandHandler.instance.registerCommand(new BedwarsLevelCommand());
        ClientCommandHandler.instance.registerCommand(new NetworkLevelCommand());

        MinecraftForge.EVENT_BUS.register(new ModuleBootstrap());
    }
}
