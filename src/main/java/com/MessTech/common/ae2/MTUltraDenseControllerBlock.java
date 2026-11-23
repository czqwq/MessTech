package com.MessTech.common.ae2;

import java.util.List;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.util.StatCollector;

import appeng.block.networking.BlockController;
import appeng.core.CreativeTab;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * The 超致密ME控制器 (ultra dense ME controller): an AE2 ME controller whose controller faces carry
 * {@link Integer#MAX_VALUE} channels instead of AE2's native 32 (see {@code docs/ultra-ae2.md} §1.4).
 *
 * <p>
 * It deliberately does not add a tile entity of its own. {@link BlockController}'s constructor already registered
 * {@code appeng.tile.networking.TileController} as the tile entity class of this block, so AE2's controller state
 * machine, multiblock validation, colour painting, power handling, animation and rendering all apply unchanged. The
 * only custom part is the {@link IMTUltraDenseControllerBlock} marker, which
 * {@code MixinPathingCalculation_UltraDense} / {@code MixinControllerFace_UltraDense} consult to raise the channel
 * capacity of every controller face on the grid the block belongs to.
 *
 * <p>
 * The appearance reuses AE2's own controller textures instead of copying them: the texture name makes
 * {@link BlockController#registerBlockIcons} register the same base, powered, column and coloured textures as the
 * original ME controller, and {@link BlockController#getRenderer()} keeps providing AE2's controller model.
 */
public class MTUltraDenseControllerBlock extends BlockController implements IMTUltraDenseControllerBlock {

    public static final MTUltraDenseControllerBlock INSTANCE = new MTUltraDenseControllerBlock();

    private MTUltraDenseControllerBlock() {
        super();
        setBlockName("ultraDenseMeController");
        setBlockTextureName("appliedenergistics2:BlockController");
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
