package com.MessTech.common.thaumcraft;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;

/**
 * Block of the ME essentia output hatch of the Small Essentia Smeltery. The machine fills it through
 * {@link MTEssentiaOutputHatchMETileEntity#addEssentia} and the tile entity pushes the essentia into the AE network. It
 * is output only, so it has no aspect filter to lock and right-clicking it does nothing.
 */
public class MTEssentiaOutputHatchMEBlock extends MTEssentiaHatchBlock {

    public MTEssentiaOutputHatchMEBlock() {
        super();
        setBlockName("essentiaOutputHatchME");
    }

    @Override
    public TileEntity createNewTileEntity(World world, int meta) {
        return new MTEssentiaOutputHatchMETileEntity();
    }

    @Override
    public boolean onBlockActivated(World world, int x, int y, int z, EntityPlayer player, int side, float hitX,
        float hitY, float hitZ) {
        return false;
    }
}
