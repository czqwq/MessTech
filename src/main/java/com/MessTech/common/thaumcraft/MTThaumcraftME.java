package com.MessTech.common.thaumcraft;

import net.minecraft.item.ItemStack;

import com.MessTech.common.block.MTBlocks;
import com.MessTech.common.items.MTItemList;

import cpw.mods.fml.common.registry.GameRegistry;
import gregtech.api.enums.Mods;

/**
 * Registers the Thaumic Energistics dependent parts (the ME essentia input and output hatches) only when ThE is loaded,
 * so the block classes that reference the ThE/AE2 API are never touched on a pack without it.
 */
public final class MTThaumcraftME {

    private MTThaumcraftME() {}

    public static void register() {
        if (!Mods.ThaumicEnergistics.isModLoaded()) return;
        MTBlocks.essentiaHatchMEBlock = new MTEssentiaHatchMEBlock();
        GameRegistry.registerBlock(MTBlocks.essentiaHatchMEBlock, MTEssentiaHatchMEItemBlock.class, "essentiaHatchME");
        GameRegistry.registerTileEntity(MTEssentiaHatchMETileEntity.class, "messtech.essentiaHatchME");
        MTItemList.MTEssentiaHatchME.set(new ItemStack(MTBlocks.essentiaHatchMEBlock, 1));

        MTBlocks.essentiaOutputHatchMEBlock = new MTEssentiaOutputHatchMEBlock();
        GameRegistry.registerBlock(MTBlocks.essentiaOutputHatchMEBlock, "essentiaOutputHatchME");
        GameRegistry.registerTileEntity(MTEssentiaOutputHatchMETileEntity.class, "messtech.essentiaOutputHatchME");
        MTItemList.MTEssentiaOutputHatchME.set(new ItemStack(MTBlocks.essentiaOutputHatchMEBlock, 1));
    }
}
