package com.MessTech.common.thaumcraft;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;

import com.cleanroommc.modularui.factory.GuiFactories;

import thaumcraft.api.aspects.IEssentiaContainerItem;

/**
 * Block of the ME essentia input hatch: right-clicking with an essentia container marks that aspect (as on the normal
 * hatch), right-clicking with an empty hand opens its GUI. Capacity and per-tick limit of an upgraded hatch are written
 * into the dropped item's NBT (GT's ME hatches do the same via {@code setItemNBT}) and read back when it is placed.
 */
public class MTEssentiaHatchMEBlock extends MTEssentiaHatchBlock {

    /** Settings captured before the tile entity is removed, consumed by {@link #getDrops}. */
    private static final Map<Long, NBTTagCompound> PENDING_DROPS = new HashMap<>();

    public MTEssentiaHatchMEBlock() {
        super();
        setBlockName("essentiaHatchME");
    }

    @Override
    public TileEntity createNewTileEntity(World world, int meta) {
        return new MTEssentiaHatchMETileEntity();
    }

    @Override
    public void onBlockPreDestroy(World world, int x, int y, int z, int meta) {
        if (world.isRemote) return;
        TileEntity tile = world.getTileEntity(x, y, z);
        if (tile instanceof MTEssentiaHatchMETileEntity hatch && hatch.hasCustomSettings()) {
            NBTTagCompound tag = new NBTTagCompound();
            hatch.writeSettings(tag);
            PENDING_DROPS.put(pack(x, y, z), tag);
        } else {
            PENDING_DROPS.remove(pack(x, y, z));
        }
    }

    @Override
    public void onBlockPlacedBy(World world, int x, int y, int z, EntityLivingBase placer, ItemStack stack) {
        super.onBlockPlacedBy(world, x, y, z, placer, stack);
        PENDING_DROPS.remove(pack(x, y, z));
        if (world.isRemote) return;
        TileEntity tile = world.getTileEntity(x, y, z);
        if (tile instanceof MTEssentiaHatchMETileEntity hatch && stack.hasTagCompound()) {
            hatch.readSettings(stack.getTagCompound());
        }
    }

    @Override
    public ArrayList<ItemStack> getDrops(World world, int x, int y, int z, int metadata, int fortune) {
        // Explosions and block breakers may ask for the drops while the tile entity still exists.
        NBTTagCompound tag;
        TileEntity tile = world.getTileEntity(x, y, z);
        if (tile instanceof MTEssentiaHatchMETileEntity hatch && hatch.hasCustomSettings()) {
            tag = new NBTTagCompound();
            hatch.writeSettings(tag);
        } else {
            tag = PENDING_DROPS.remove(pack(x, y, z));
        }
        if (tag == null) return super.getDrops(world, x, y, z, metadata, fortune);
        ArrayList<ItemStack> drops = new ArrayList<>(1);
        ItemStack stack = new ItemStack(this);
        stack.setTagCompound(tag);
        drops.add(stack);
        return drops;
    }

    @Override
    public boolean onBlockActivated(World world, int x, int y, int z, EntityPlayer player, int side, float hitX,
        float hitY, float hitZ) {
        if (world.isRemote) return true;
        TileEntity tile = world.getTileEntity(x, y, z);
        if (!(tile instanceof MTEssentiaHatchMETileEntity)) return false;

        ItemStack held = player.getHeldItem();
        if (held != null && held.getItem() instanceof IEssentiaContainerItem container
            && container.getAspects(held) != null
            && container.getAspects(held)
                .size() > 0) {
            return super.onBlockActivated(world, x, y, z, player, side, hitX, hitY, hitZ);
        }
        GuiFactories.tileEntity()
            .open(player, x, y, z);
        return true;
    }

    private static long pack(int x, int y, int z) {
        return ((long) (x & 0x3FFFFFF) << 38) | ((long) (z & 0x3FFFFFF) << 12) | (y & 0xFFF);
    }
}
