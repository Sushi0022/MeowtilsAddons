package com.github.tewxx.meowtilsaddons.modules;

import wtf.tatp.meowtils.gui.Module;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent.Action;
import net.minecraft.init.Blocks;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.BlockPos;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.ChatComponentText;
import net.minecraft.world.World;

public class AntiObbyMisplace extends Module {
    public AntiObbyMisplace() {
        super("AntiObbyMisplace", "antiObbyMisplaceKey", "antiObbyMisplace", Module.Category.Advanced);
        try { this.tooltip("Prevents obsidian misplacements"); } catch (Throwable ignored) {}
    }

    @SubscribeEvent
    public void onRightClick(PlayerInteractEvent event) {
        try {
            if (!this.getState()) return;
            if (this.mc == null || this.mc.thePlayer == null) return;
            if (event == null || event.entityPlayer == null) return;
            if (event.action != Action.RIGHT_CLICK_BLOCK) return;

            ItemStack held = event.entityPlayer.getCurrentEquippedItem();
            if (held == null) return;
            Item obsidianItem = Item.getItemFromBlock(Blocks.obsidian);
            if (held.getItem() != obsidianItem) return;

            BlockPos targetPos = event.pos == null || event.face == null ? null : event.pos.offset(event.face);
            if (targetPos == null) return;

            World world = event.world;
            if (world == null) return;

            if (!isAdjacentToBed(world, targetPos)) {
                event.setCanceled(true);
                try {
                   // this.mc.thePlayer.addChatMessage(new ChatComponentText("§cObsidian can only be placed next to your bed."));
                } catch (Throwable ignored) {}
            }
        } catch (Throwable ignored) {}
    }

    private boolean isAdjacentToBed(World world, BlockPos pos) {
        try {
            for (EnumFacing f : EnumFacing.values()) {
                BlockPos p = pos.offset(f);
                if (world.getBlockState(p).getBlock() == Blocks.bed) {
                    return true;
                }
            }
        } catch (Throwable ignored) {}
        return false;
    }
}
