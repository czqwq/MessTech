package com.MessTech.common.thaumcraft;

import java.util.List;

import net.minecraft.client.renderer.texture.IIconRegister;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.IIcon;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import gregtech.api.GregTechAPI;

/**
 * The LEG upgrade items: metadata 0 is the empty upgrade (crafting ingredient only), 1..9 unlock the essentia category
 * of the same index on a Large Essentia Generator by right-clicking the controller with it.
 */
public class MTEssentiaUpgradeItem extends Item {

    /** Texture name per metadata; index = upgrade bit / esssentia category. */
    public static final String[] NAMES = { "null", "air", "thermal", "unstable", "victus", "tainted", "mechanics",
        "spirit", "radiation", "electric" };

    @SideOnly(Side.CLIENT)
    private IIcon[] icons;

    public MTEssentiaUpgradeItem() {
        setUnlocalizedName("messtech.essentiaUpgrade");
        setHasSubtypes(true);
        setMaxDamage(0);
        setCreativeTab(GregTechAPI.TAB_GREGTECH);
    }

    public static ItemStack getStack(int upgrade) {
        return new ItemStack(com.MessTech.common.items.MTItems.essentiaUpgrade, 1, upgrade);
    }

    @Override
    public String getUnlocalizedName(ItemStack stack) {
        return "item.messtech.essentiaUpgrade." + stack.getItemDamage();
    }

    @Override
    @SideOnly(Side.CLIENT)
    public IIcon getIconFromDamage(int meta) {
        return icons[Math.min(meta, icons.length - 1)];
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void registerIcons(IIconRegister register) {
        icons = new IIcon[NAMES.length];
        for (int i = 0; i < NAMES.length; i++) {
            icons[i] = register.registerIcon("messtech:upgradeEssentia/" + NAMES[i]);
        }
    }

    @Override
    @SideOnly(Side.CLIENT)
    @SuppressWarnings({ "unchecked", "rawtypes" })
    public void getSubItems(Item item, CreativeTabs tab, List list) {
        for (int i = 0; i < NAMES.length; i++) {
            list.add(new ItemStack(item, 1, i));
        }
    }
}
