package com.MessTech.common.thaumcraft;

import java.util.List;

import net.minecraft.block.Block;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.StatCollector;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/** Item form of the ME essentia input hatch: right-click hints, plus the upgraded capacity carried in its NBT. */
public class MTEssentiaHatchMEItemBlock extends ItemBlock {

    public MTEssentiaHatchMEItemBlock(Block block) {
        super(block);
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void addInformation(ItemStack stack, EntityPlayer player, List<String> tooltip, boolean advanced) {
        tooltip.add(StatCollector.translateToLocal("machine.essentiahatch.me.tooltip.0"));
        tooltip.add(StatCollector.translateToLocal("machine.essentiahatch.me.tooltip.1"));
        if (!stack.hasTagCompound()) return;
        NBTTagCompound tag = stack.getTagCompound();
        if (tag.hasKey(MTEssentiaHatchMETileEntity.NBT_CAPACITY)) {
            tooltip.add(
                StatCollector.translateToLocalFormatted(
                    "machine.essentiahatch.me.tooltip.capacity",
                    tag.getInteger(MTEssentiaHatchMETileEntity.NBT_CAPACITY)));
        }
        if (tag.hasKey(MTEssentiaHatchMETileEntity.NBT_MAX_INPUT)) {
            tooltip.add(
                StatCollector.translateToLocalFormatted(
                    "machine.essentiahatch.me.tooltip.max_input",
                    tag.getInteger(MTEssentiaHatchMETileEntity.NBT_MAX_INPUT)));
        }
    }
}
