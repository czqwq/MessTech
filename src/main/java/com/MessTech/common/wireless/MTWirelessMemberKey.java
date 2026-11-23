package com.MessTech.common.wireless;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;

/**
 * Immutable (dimension, x, y, z) key of one wireless ME network member, i.e. an ME hatch that was bound to a wireless
 * ME
 * network core. Members are stored in {@link MTWirelessSavedData}; the block side is deliberately not part of the key
 * so one hatch position can only belong to one network.
 */
public final class MTWirelessMemberKey {

    private static final String TAG_DIM = "d";
    private static final String TAG_X = "x";
    private static final String TAG_Y = "y";
    private static final String TAG_Z = "z";

    public final int dim;
    public final int x;
    public final int y;
    public final int z;

    public MTWirelessMemberKey(int dim, int x, int y, int z) {
        this.dim = dim;
        this.x = x;
        this.y = y;
        this.z = z;
    }

    /** @return the key of {@code te}, or {@code null} when the tile entity is not in a world. */
    public static MTWirelessMemberKey of(TileEntity te) {
        if (te == null) return null;
        World world = te.getWorldObj();
        if (world == null || world.provider == null) return null;
        return new MTWirelessMemberKey(world.provider.dimensionId, te.xCoord, te.yCoord, te.zCoord);
    }

    /** @return {@code {"d","x","y","z"}} as specified by the wireless ME network contract. */
    public NBTTagCompound write() {
        NBTTagCompound tag = new NBTTagCompound();
        tag.setInteger(TAG_DIM, this.dim);
        tag.setInteger(TAG_X, this.x);
        tag.setInteger(TAG_Y, this.y);
        tag.setInteger(TAG_Z, this.z);
        return tag;
    }

    /** @return the member key, or {@code null} for malformed input. */
    public static MTWirelessMemberKey read(NBTTagCompound tag) {
        if (tag == null || !tag.hasKey(TAG_DIM) || !tag.hasKey(TAG_X) || !tag.hasKey(TAG_Y) || !tag.hasKey(TAG_Z)) {
            return null;
        }
        return new MTWirelessMemberKey(
            tag.getInteger(TAG_DIM),
            tag.getInteger(TAG_X),
            tag.getInteger(TAG_Y),
            tag.getInteger(TAG_Z));
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof MTWirelessMemberKey key)) return false;
        return this.dim == key.dim && this.x == key.x && this.y == key.y && this.z == key.z;
    }

    @Override
    public int hashCode() {
        int result = this.dim;
        result = 31 * result + this.x;
        result = 31 * result + this.y;
        result = 31 * result + this.z;
        return result;
    }

    @Override
    public String toString() {
        return "MTWirelessMemberKey[" + this.dim + ", " + this.x + ", " + this.y + ", " + this.z + "]";
    }
}
