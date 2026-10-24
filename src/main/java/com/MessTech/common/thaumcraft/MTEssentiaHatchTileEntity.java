package com.MessTech.common.thaumcraft;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.NetworkManager;
import net.minecraft.network.Packet;
import net.minecraft.network.play.server.S35PacketUpdateTileEntity;
import net.minecraft.tileentity.TileEntity;
import net.minecraftforge.common.util.ForgeDirection;

import thaumcraft.api.ThaumcraftApiHelper;
import thaumcraft.api.aspects.Aspect;
import thaumcraft.api.aspects.AspectList;
import thaumcraft.api.aspects.IAspectContainer;
import thaumcraft.api.aspects.IEssentiaTransport;

/**
 * The essentia input hatch of the Large Essentia Generator. Port of GoodGenerator's {@code MTEEssentiaHatch}: it holds
 * up to {@value #CAPACITY} essentia, pulls one unit per tick from adjacent Thaumcraft tubes, and only accepts aspects
 * whose category the generator has unlocked ({@link #mState}, written by the controller from its upgrade bit mask).
 */
public class MTEssentiaHatchTileEntity extends TileEntity implements IAspectContainer, IEssentiaTransport {

    public static final int CAPACITY = 1000;
    private static final int SUCTION = 256;

    /** Bit mask of accepted essentia categories, set by {@code MTLargeEssentiaGenerator}. */
    public int mState = 1;

    private Aspect lockedAspect;
    private final AspectList aspects = new AspectList();

    /** @return Essentia capacity of this hatch; the ME variant raises it to {@link Integer#MAX_VALUE}. */
    public int getCapacity() {
        return CAPACITY;
    }

    public void setLockedAspect(Aspect aspect) {
        this.lockedAspect = aspect;
        markChanged();
    }

    public Aspect getLockedAspect() {
        return lockedAspect;
    }

    /** @return Essentia stored right now; the ME variant's GUI shows it. */
    public int getStoredAmount() {
        return storedAmount();
    }

    private boolean isAccepted(Aspect aspect) {
        int type = MTEssentiaEnergyData.getAspectTypeIndex(aspect);
        return type != MTEssentiaEnergyData.TYPE_INVALID && (mState & (1 << type)) != 0;
    }

    private int storedAmount() {
        long amount = 0;
        for (Aspect aspect : aspects.getAspects()) {
            amount += aspects.getAmount(aspect);
        }
        return (int) Math.min(amount, Integer.MAX_VALUE);
    }

    private void markChanged() {
        markDirty();
        if (worldObj != null) {
            worldObj.markBlockForUpdate(xCoord, yCoord, zCoord);
        }
    }

    @Override
    public void readFromNBT(NBTTagCompound tag) {
        super.readFromNBT(tag);
        lockedAspect = Aspect.getAspect(tag.getString("mLocked"));
        mState = tag.getInteger("mState");
        aspects.readFromNBT(tag);
    }

    @Override
    public void writeToNBT(NBTTagCompound tag) {
        super.writeToNBT(tag);
        tag.setString("mLocked", lockedAspect == null ? "" : lockedAspect.getTag());
        tag.setInteger("mState", mState);
        aspects.writeToNBT(tag);
    }

    @Override
    public Packet getDescriptionPacket() {
        NBTTagCompound tag = new NBTTagCompound();
        writeToNBT(tag);
        return new S35PacketUpdateTileEntity(xCoord, yCoord, zCoord, 0, tag);
    }

    @Override
    public void onDataPacket(NetworkManager net, S35PacketUpdateTileEntity packet) {
        readFromNBT(packet.func_148857_g());
    }

    @Override
    public void updateEntity() {
        if (worldObj == null || worldObj.isRemote) return;
        fillFromPipes();
    }

    /** Pulls one unit of a matching aspect from every adjacent transport that offers it. */
    private void fillFromPipes() {
        if (storedAmount() >= getCapacity()) return;
        for (ForgeDirection side : ForgeDirection.VALID_DIRECTIONS) {
            TileEntity neighbour = ThaumcraftApiHelper.getConnectableTile(worldObj, xCoord, yCoord, zCoord, side);
            if (!(neighbour instanceof IEssentiaTransport pipe)) continue;
            // The neighbour's face towards us is the opposite direction, as in Thaumcraft's own tubes.
            ForgeDirection pipeFace = side.getOpposite();
            if (!pipe.canOutputTo(pipeFace)) continue;
            if (getSuctionAmount(side) < pipe.getMinimumSuction()) continue;
            if (pipe.getSuctionAmount(pipeFace) >= getSuctionAmount(side)) continue;

            Aspect offered = pipe.getEssentiaType(pipeFace);
            if (offered == null) continue;
            if (!isAccepted(offered)) continue;
            if (lockedAspect != null && !lockedAspect.equals(offered)) continue;

            int taken = pipe.takeEssentia(offered, 1, pipeFace);
            if (taken > 0) {
                addToContainer(offered, taken);
                if (storedAmount() >= getCapacity()) return;
            }
        }
    }

    // region IAspectContainer

    @Override
    public AspectList getAspects() {
        return aspects;
    }

    @Override
    public void setAspects(AspectList aspectList) {
        aspects.aspects.clear();
        if (aspectList != null) aspects.add(aspectList);
        markChanged();
    }

    @Override
    public boolean doesContainerAccept(Aspect aspect) {
        if (aspect == null || !isAccepted(aspect)) return false;
        if (lockedAspect != null) return lockedAspect.equals(aspect) && storedAmount() < getCapacity();
        return storedAmount() < getCapacity();
    }

    @Override
    public int addToContainer(Aspect aspect, int amount) {
        if (!doesContainerAccept(aspect)) return amount;
        int accepted = Math.min(getCapacity() - storedAmount(), amount);
        if (accepted <= 0) return amount;
        aspects.add(aspect, accepted);
        markChanged();
        return amount - accepted;
    }

    @Override
    public boolean takeFromContainer(Aspect aspect, int amount) {
        if (!doesContainerContainAmount(aspect, amount)) return false;
        aspects.remove(aspect, amount);
        markChanged();
        return true;
    }

    @Deprecated
    @Override
    public boolean takeFromContainer(AspectList aspectList) {
        if (aspectList == null || !doesContainerContain(aspectList)) return false;
        for (Aspect aspect : aspectList.getAspects()) {
            aspects.remove(aspect, aspectList.getAmount(aspect));
        }
        markChanged();
        return true;
    }

    @Override
    public boolean doesContainerContainAmount(Aspect aspect, int amount) {
        return aspects.getAmount(aspect) >= amount;
    }

    @Deprecated
    @Override
    public boolean doesContainerContain(AspectList aspectList) {
        if (aspectList == null) return false;
        for (Aspect aspect : aspectList.getAspects()) {
            if (!doesContainerContainAmount(aspect, aspectList.getAmount(aspect))) return false;
        }
        return true;
    }

    @Override
    public int containerContains(Aspect aspect) {
        return aspects.getAmount(aspect);
    }
    // endregion

    // region IEssentiaTransport

    @Override
    public boolean isConnectable(ForgeDirection face) {
        return true;
    }

    @Override
    public boolean canInputFrom(ForgeDirection face) {
        return true;
    }

    @Override
    public boolean canOutputTo(ForgeDirection face) {
        return false;
    }

    @Override
    public void setSuction(Aspect aspect, int amount) {}

    @Override
    public Aspect getSuctionType(ForgeDirection face) {
        return lockedAspect;
    }

    @Override
    public int getSuctionAmount(ForgeDirection face) {
        return SUCTION;
    }

    @Override
    public int takeEssentia(Aspect aspect, int amount, ForgeDirection face) {
        return 0;
    }

    @Override
    public int addEssentia(Aspect aspect, int amount, ForgeDirection face) {
        return amount - addToContainer(aspect, amount);
    }

    @Override
    public Aspect getEssentiaType(ForgeDirection face) {
        Aspect[] stored = aspects.getAspects();
        return stored.length == 0 ? null : stored[0];
    }

    @Override
    public int getEssentiaAmount(ForgeDirection face) {
        return storedAmount();
    }

    @Override
    public int getMinimumSuction() {
        return Integer.MAX_VALUE;
    }

    @Override
    public boolean renderExtendedTube() {
        return true;
    }
    // endregion
}
