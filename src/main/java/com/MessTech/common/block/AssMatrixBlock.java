package com.MessTech.common.block;

import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.client.renderer.texture.IIconRegister;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import gregtech.api.GregTechAPI;
import lombok.Getter;
import lombok.Setter;

/**
 * Assembly Matrix Block - Tier 1.
 * <p>
 * One of the two structure blocks used by {@code MTAssFactory} (structure letter 'I').
 * The tier of this block ({@link #getLevelTier()}) is read
 * back by {@code StructureUtility#ofBlocksTiered} while the machine checks its structure, and is
 * written into the machine's {@code LevelTier} field (1 for this block, 2 for
 * {@link AdvAssMatrixBlock}).
 */
@SuppressWarnings("SpellCheckingInspection")
public class AssMatrixBlock extends Block {

    /** Level tier of this block: 1 = basic Assembly Matrix Block. */
    @Getter
    @Setter
    private int LevelTier = 1;

    public AssMatrixBlock() {
        super(Material.iron);
        this.setBlockName("AssMatrixBlock");
        this.setBlockTextureName("messtech:AssMatrixBlock");
        this.setHardness(6.0F);
        this.setResistance(20.0F);
        this.setStepSound(soundTypeMetal);
        this.setCreativeTab(GregTechAPI.TAB_GREGTECH);
    }

    public static AssMatrixBlock getBlock() {
        return MTBlocks.assMatrixBlock;
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

    @Override
    @SideOnly(Side.CLIENT)
    public void registerBlockIcons(IIconRegister aIconRegister) {
        this.blockIcon = aIconRegister.registerIcon(this.getTextureName());
    }
}
