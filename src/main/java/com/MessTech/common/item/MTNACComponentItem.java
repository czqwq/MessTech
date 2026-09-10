package com.MessTech.common.item;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import net.minecraft.client.renderer.texture.IIconRegister;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.IIcon;
import net.minecraft.util.StatCollector;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import gregtech.api.enums.Mods;
import gregtech.common.tileentities.machines.multi.nanochip.util.CircuitComponent;

/**
 * Real item forms for NAC CircuitComponents that only exist as fake items.
 * <p>
 * Metadata values are identical to {@link CircuitComponent#metaId}, so fake/real conversion can be
 * done by item id + metadata. Textures and tooltips mirror the original fake CircuitComponent item.
 */
public class MTNACComponentItem extends Item {

    public static final String TEXTURE_LOCATION = ":gt.circuitcomponent/";
    public static MTNACComponentItem INSTANCE;

    private final Map<Integer, IIcon> iconMap = new HashMap<>();

    public MTNACComponentItem() {
        setUnlocalizedName("messtech.nacComponent");
        setTextureName("appliedenergistics2:ItemCreativeStorageCell"); // replaced in registerIcons
        setHasSubtypes(true);
        setMaxDamage(0);
        setMaxStackSize(64);
        setCreativeTab(CreativeTabs.tabMisc);
        INSTANCE = this;
    }

    public static ItemStack getStack(CircuitComponent component, int amount) {
        return new ItemStack(INSTANCE, amount, component.metaId);
    }

    public static boolean isStackOfThis(ItemStack stack) {
        return stack != null && stack.getItem() == INSTANCE;
    }

    @Override
    public String getUnlocalizedName(ItemStack stack) {
        return "messtech.nacComponent";
    }

    @Override
    public String getItemStackDisplayName(ItemStack stack) {
        CircuitComponent component = getComponent(stack);
        if (component == null) return super.getItemStackDisplayName(stack);
        // Unprocessed components are displayed as "Packaged <real item>" like the original fake item.
        if (!component.isProcessed && component.realComponent != null && component.realComponent.get() != null) {
            return StatCollector.translateToLocalFormatted(
                "gt.circuitcomponent.base",
                component.realComponent.get()
                    .getDisplayName());
        }
        return component.getLocalizedName();
    }

    @Override
    public void addInformation(ItemStack stack, EntityPlayer player, List<String> tooltip, boolean advanced) {
        CircuitComponent component = getComponent(stack);
        if (component == null) return;
        tooltip.add(StatCollector.translateToLocal("gt.circuitcomponent.tooltip.base"));
        tooltip.add(
            StatCollector.translateToLocal(
                component.isProcessed ? "gt.circuitcomponent.tooltip.pc.base" : "gt.circuitcomponent.tooltip.cc.base"));
        super.addInformation(stack, player, tooltip, advanced);
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void getSubItems(Item item, CreativeTabs tab, List list) {
        for (CircuitComponent component : CircuitComponent.VALUES) {
            list.add(getStack(component, 1));
        }
    }

    @Override
    @SideOnly(Side.CLIENT)
    public IIcon getIconFromDamage(int meta) {
        IIcon icon = iconMap.get(meta);
        return icon != null ? icon : iconMap.get(-1);
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void registerIcons(IIconRegister register) {
        for (CircuitComponent component : CircuitComponent.VALUES) {
            if (component.iconString != null) {
                iconMap.put(
                    component.metaId,
                    register.registerIcon(Mods.GregTech.ID + TEXTURE_LOCATION + component.iconString));
            }
        }
        iconMap.put(-1, register.registerIcon(Mods.GregTech.ID + TEXTURE_LOCATION + "circuitcomponent_default"));
    }

    private static CircuitComponent getComponent(ItemStack stack) {
        if (stack == null || stack.getItemDamage() < 0) return null;
        return CircuitComponent.tryGetFromFakeStack(stack);
    }
}
