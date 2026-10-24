package com.MessTech.common.thaumcraft;

import java.util.List;

import net.minecraft.block.Block;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;
import net.minecraft.util.StatCollector;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/** Item form of the essentia input hatch: its two right-click hints live in the lang files. */
public class MTEssentiaHatchItemBlock extends ItemBlock {

    public MTEssentiaHatchItemBlock(Block block) {
        super(block);
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void addInformation(ItemStack stack, EntityPlayer player, List<String> tooltip, boolean advanced) {
        tooltip.add(StatCollector.translateToLocal("machine.essentiahatch.tooltip.0"));
        tooltip.add(StatCollector.translateToLocal("machine.essentiahatch.tooltip.1"));
    }
}
