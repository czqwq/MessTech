package com.MessTech.common.items;

import net.minecraft.client.renderer.texture.IIconRegister;
import net.minecraft.item.ItemStack;
import net.minecraft.util.IIcon;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import gregtech.api.items.ItemRadioactiveCellIC;

/**
 * Burnable MessTech fuel rod (Transcendent Metal single/dual/quad).
 * <p>
 * Extending GT's {@link ItemRadioactiveCellIC} gives the IC2 reactor component behaviour (pulses, heat
 * redistribution, depletion), MTReactor's {@code IReactorComponent} simulation, radiation, the NEI nuclear
 * fake recipe and the auto input/output logic for free. The real fuel state lives in the NBT {@code advDmg}
 * tag while GT maintains the vanilla 0..99 damage bar.
 * <p>
 * Textures use MessTech's own domain ({@code messtech:FuelRod/...}), hence the icon registration override.
 */
public class MTFuelRod extends ItemRadioactiveCellIC {

    private final String texturePath;
    private IIcon icon;

    public MTFuelRod(String unlocalized, String english, int cells, int maxDamage, float energy, int radiation,
        float heat, ItemStack depleted, boolean mox, float heatBonus, String texturePath) {
        super(unlocalized, english, cells, maxDamage, energy, radiation, heat, depleted, mox, heatBonus);
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
