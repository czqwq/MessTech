package com.MessTech.common.items;

import net.minecraft.client.renderer.texture.IIconRegister;
import net.minecraft.util.IIcon;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import gregtech.common.items.ItemDepletedCell;

/**
 * Depleted MessTech fuel rod (Transcendent Metal single/dual/quad).
 * <p>
 * Extends GT's {@link ItemDepletedCell}, whose reactor behaviour is fully disabled (no heat, no output, no
 * pulse transfer), so it only works as an inert reactor filler / recycling input, exactly like GT's depleted
 * rods.
 */
public class MTDepletedFuelRod extends ItemDepletedCell {

    private final String texturePath;
    private IIcon icon;

    public MTDepletedFuelRod(String unlocalized, String english, int radiation, String texturePath) {
        super(unlocalized, english, radiation);
        this.texturePath = texturePath;
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void registerIcons(IIconRegister register) {
        icon = register.registerIcon(MTItems.TEXTURE_DOMAIN + ":" + texturePath);
        mIcon = icon;
    }

    @Override
    @SideOnly(Side.CLIENT)
    public IIcon getIconFromDamage(int meta) {
        return icon;
    }
}
