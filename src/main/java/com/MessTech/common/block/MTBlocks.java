package com.MessTech.common.block;

import net.minecraft.block.Block;
import net.minecraft.item.ItemStack;

import com.MessTech.common.items.MTItemList;
import com.MessTech.common.thaumcraft.MTEssentiaHatchBlock;
import com.MessTech.common.thaumcraft.MTEssentiaHatchItemBlock;
import com.MessTech.common.thaumcraft.MTEssentiaHatchTileEntity;
import com.MessTech.common.thaumcraft.MTThaumcraftME;

import cpw.mods.fml.common.registry.GameRegistry;

/**
 * Registry of MessTech's standalone structure blocks: the two Assembly Matrix blocks are the tiered 'I' structure
 * element
 * of {@code com.MessTech.common.machine.MTAssFactory} ({@link #assMatrixBlock} Tier 1, {@link #advAssMatrixBlock} Tier
 * 2);
 * the essentia input hatches belong to the Large Essentia Generator structure.
 */
public final class MTBlocks {

    /** Assembly Matrix Block, Tier 1. */
    public static final AssMatrixBlock assMatrixBlock = new AssMatrixBlock();
    /** Advanced Assembly Matrix Block, Tier 2. */
    public static final AdvAssMatrixBlock advAssMatrixBlock = new AdvAssMatrixBlock();
    /** Essentia input hatch of the Large Essentia Generator. */
    public static final MTEssentiaHatchBlock essentiaHatchBlock = new MTEssentiaHatchBlock();
    /** ME essentia input hatch; only registered when Thaumic Energistics is loaded (see {@link MTThaumcraftME}). */
    public static Block essentiaHatchMEBlock;

    private MTBlocks() {}

    /** Registers the blocks into the {@link GameRegistry}. Called before machine registration. */
    public static void registerBlocks() {
        GameRegistry.registerBlock(assMatrixBlock, "AssMatrixBlock");
        GameRegistry.registerBlock(advAssMatrixBlock, "AdvAssMatrixBlock");
        GameRegistry.registerBlock(essentiaHatchBlock, MTEssentiaHatchItemBlock.class, "essentiaHatch");
        GameRegistry.registerTileEntity(MTEssentiaHatchTileEntity.class, "messtech.essentiaHatch");
        MTItemList.MTEssentiaHatch.set(new ItemStack(essentiaHatchBlock, 1));
        MTThaumcraftME.register();
    }
}
