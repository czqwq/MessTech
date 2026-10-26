package com.MessTech.common.items;

import java.util.List;

import net.minecraft.client.renderer.texture.IIconRegister;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.IIcon;
import net.minecraft.util.StatCollector;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import gregtech.api.GregTechAPI;

/**
 * The comb processing module (蜂窝处理模块): the non-consumed catalyst of the Chemical Twister's mode 2 (蜂窝处理者).
 * <p>
 * Every recipe of {@code MTRecipeMaps.combProcessingRecipes} carries it as an input of stack size 0, which GT reads as
 * "present in an input bus, never consumed" - the same convention as an integrated circuit or the QFT catalyst. It is
 * also the recipe pool's NEI display stack, see {@code MTRecipeMaps#combProcessingRecipes}.
 */
public class MTItemCombProcessingModule extends Item {

    /** 512x512 source image, see {@code assets/messtech/textures/items/comb_processing_module.png}. */
    private static final String TEXTURE = "messtech:comb_processing_module";

    @SideOnly(Side.CLIENT)
    private IIcon icon;

    public MTItemCombProcessingModule() {
        // "messtech." prefix like every other item here: the key is item.messtech.combProcessingModule.name.
        setUnlocalizedName("messtech.combProcessingModule");
        setCreativeTab(GregTechAPI.TAB_GREGTECH);
        setMaxStackSize(64);
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void registerIcons(IIconRegister register) {
        icon = register.registerIcon(TEXTURE);
    }

    @Override
    @SideOnly(Side.CLIENT)
    public IIcon getIconFromDamage(int damage) {
        return icon;
    }

    /** House style ({@code docs/knowledge.md}): colours live in the lang files, one translated key per line. */
    @Override
    @SideOnly(Side.CLIENT)
    public void addInformation(ItemStack stack, EntityPlayer player, List<String> tooltip, boolean advanced) {
        tooltip.add(StatCollector.translateToLocal("messtech.combProcessingModule.tooltip"));
    }
}
