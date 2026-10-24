package com.MessTech.common.thaumcraft;

import net.minecraft.block.Block;
import net.minecraft.block.BlockContainer;
import net.minecraft.block.material.Material;
import net.minecraft.client.renderer.texture.IIconRegister;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EnumCreatureType;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.IIcon;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import gregtech.api.GregTechAPI;
import gregtech.api.util.GTUtility;
import thaumcraft.api.aspects.IEssentiaContainerItem;

/**
 * The essentia input hatch block of the Large Essentia Generator. Right-clicking it with an essentia container (a jar,
 * a phial, ...) locks the hatch to that aspect, right-clicking with an empty hand clears the filter.
 */
public class MTEssentiaHatchBlock extends BlockContainer {

    public MTEssentiaHatchBlock() {
        super(Material.iron);
        setBlockName("essentiaHatch");
        setBlockTextureName("messtech:essentiaHatch");
        setHardness(9.0F);
        setResistance(5.0F);
        setHarvestLevel("wrench", 2);
        setCreativeTab(GregTechAPI.TAB_GREGTECH);
        GregTechAPI.registerMachineBlock(this, -1);
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void registerBlockIcons(IIconRegister register) {
        blockIcon = register.registerIcon(getTextureName());
    }

    @Override
    @SideOnly(Side.CLIENT)
    public IIcon getIcon(int side, int meta) {
        return blockIcon;
    }

    @Override
    public void onBlockAdded(World world, int x, int y, int z) {
        if (GregTechAPI.isMachineBlock(this, world.getBlockMetadata(x, y, z))) {
            GregTechAPI.causeMachineUpdate(world, x, y, z);
        }
    }

    @Override
    public void breakBlock(World world, int x, int y, int z, Block block, int meta) {
        if (GregTechAPI.isMachineBlock(this, meta)) {
            GregTechAPI.causeMachineUpdate(world, x, y, z);
        }
        world.removeTileEntity(x, y, z);
    }

    @Override
    public boolean onBlockActivated(World world, int x, int y, int z, EntityPlayer player, int side, float hitX,
        float hitY, float hitZ) {
        if (world.isRemote) return false;
        TileEntity tile = world.getTileEntity(x, y, z);
        if (!(tile instanceof MTEssentiaHatchTileEntity hatch)) return false;

        ItemStack held = player.getHeldItem();
        if (held == null) {
            hatch.setLockedAspect(null);
            GTUtility.sendChatTrans(player, "machine.essentiahatch.chat.clear");
            return true;
        }
        if (!(held.getItem() instanceof IEssentiaContainerItem container)) return false;
        if (container.getAspects(held) == null || container.getAspects(held)
            .size() < 1) return false;

        var locked = container.getAspects(held)
            .getAspects()[0];
        hatch.setLockedAspect(locked);
        GTUtility.sendChatTrans(player, "machine.essentiahatch.chat.locked", locked.getLocalizedDescription());
        world.markBlockForUpdate(x, y, z);
        return true;
    }

    @Override
    public boolean canBeReplacedByLeaves(IBlockAccess world, int x, int y, int z) {
        return false;
    }

    @Override
    public boolean canEntityDestroy(IBlockAccess world, int x, int y, int z, Entity entity) {
        return false;
    }

    @Override
    public boolean canCreatureSpawn(EnumCreatureType type, IBlockAccess world, int x, int y, int z) {
        return false;
    }

    @Override
    public TileEntity createNewTileEntity(World world, int meta) {
        return new MTEssentiaHatchTileEntity();
    }
}
