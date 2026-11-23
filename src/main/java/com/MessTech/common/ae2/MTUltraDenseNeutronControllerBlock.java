package com.MessTech.common.ae2;

import java.util.List;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.util.StatCollector;

import appeng.block.networking.BlockCreativeEnergyController;
import appeng.core.CreativeTab;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * The 超致密ME中子注入控制器 (ultra dense ME neutron infused controller): the
 * {@link BlockCreativeEnergyController} (ME中子注入控制器 / Neutronium-Infused ME Controller) counterpart of
 * {@link MTUltraDenseControllerBlock}, with the same {@link Integer#MAX_VALUE} channels per controller face.
 *
 * <p>
 * As with the normal ultra dense controller there is no tile entity of this mod:
 * {@link BlockCreativeEnergyController}'s constructor replaced the tile entity class with
 * {@code appeng.tile.networking.TileCreativeEnergyController}, which AE2 has already registered, so power, infinite
 * energy, multiblock handling and rendering are the stock AE2 behaviour. The
 * {@link IMTUltraDenseControllerBlock} marker is what the pathing mixins use to detect this block.
 *
 * <p>
 * The texture reuses AE2's neutron infused controller textures, and the AE2 tooltip for
 * {@code BlockCreativeEnergyController} is inherited from its {@code addInformation}.
 */
public class MTUltraDenseNeutronControllerBlock extends BlockCreativeEnergyController
    implements IMTUltraDenseControllerBlock {

    public static final MTUltraDenseNeutronControllerBlock INSTANCE = new MTUltraDenseNeutronControllerBlock();

    private MTUltraDenseNeutronControllerBlock() {
        super();
        setBlockName("ultraDenseNeutronController");
        setBlockTextureName("appliedenergistics2:BlockCreativeEnergyController");
        setHardness(6.0F);
        setResistance(6.0F);
        setCreativeTab(CreativeTab.instance);
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void addInformation(ItemStack stack, EntityPlayer player, List<String> tooltip, boolean advanced) {
        super.addInformation(stack, player, tooltip, advanced);
        tooltip.add(StatCollector.translateToLocal("mtultra.tooltip.controller"));
    }
}
