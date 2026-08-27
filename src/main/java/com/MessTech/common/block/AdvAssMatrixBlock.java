package com.MessTech.common.block;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;

/**
 * Advanced Assembly Matrix Block - Tier 2.
 * <p>
 * The upgraded variant of {@link AssMatrixBlock}; its {@link #getLevelTier()} is 2, so an
 * {@code MTAssFactory} built entirely with this block records {@code LevelTier = 2}.
 */
@SuppressWarnings("SpellCheckingInspection")
public class AdvAssMatrixBlock extends AssMatrixBlock {

    public AdvAssMatrixBlock() {
        super();
        this.setLevelTier(2);
        this.setBlockName("AdvAssMatrixBlock");
        this.setBlockTextureName("megatech:AdvAssMatrixBlock");
    }

    public static AdvAssMatrixBlock getBlock() {
        return MTBlocks.advAssMatrixBlock;
    }

    public static Item getItem() {
        return Item.getItemFromBlock(getBlock());
    }

    public static ItemStack getItemStack() {
        return getItemStack(1);
    }

    public static ItemStack getItemStack(int aAmount) {
        return new ItemStack(getBlock(), aAmount);
    }
}
